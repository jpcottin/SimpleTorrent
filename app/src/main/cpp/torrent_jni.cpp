#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <dirent.h>
#include <fstream>
#include <iomanip>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <vector>

#include <libtorrent/alert_types.hpp>
#include <libtorrent/bencode.hpp>
#include <libtorrent/create_torrent.hpp>
#include <libtorrent/magnet_uri.hpp>
#include <libtorrent/peer_info.hpp>
#include <libtorrent/read_resume_data.hpp>
#include <libtorrent/session.hpp>
#include <libtorrent/session_handle.hpp>
#include <libtorrent/torrent_flags.hpp>
#include <libtorrent/torrent_handle.hpp>
#include <libtorrent/torrent_info.hpp>
#include <libtorrent/torrent_status.hpp>
#include <libtorrent/write_resume_data.hpp>

#define LOG_TAG "SimpleTorrent"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    std::unique_ptr<lt::session> g_session;
    std::string g_save_path;
    std::string g_state_dir;
    std::mutex g_mutex;
}

// ── helpers ──────────────────────────────────────────────────────────────────

static const char* state_str(lt::torrent_status::state_t s) {
    switch (s) {
        case lt::torrent_status::checking_files:       return "checking files";
        case lt::torrent_status::downloading_metadata: return "fetching metadata";
        case lt::torrent_status::downloading:          return "downloading";
        case lt::torrent_status::finished:             return "finished";
        case lt::torrent_status::seeding:              return "seeding";
        case lt::torrent_status::checking_resume_data: return "checking resume";
        default:                                       return "unknown";
    }
}

static std::string json_str(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 2);
    out += '"';
    for (unsigned char c : s) {
        if      (c == '"')  { out += "\\\""; }
        else if (c == '\\') { out += "\\\\"; }
        else if (c == '\n') { out += "\\n";  }
        else if (c == '\r') { out += "\\r";  }
        else if (c == '\t') { out += "\\t";  }
        else if (c < 0x20)  {
            char buf[7];
            std::snprintf(buf, sizeof(buf), "\\u%04x", c);
            out += buf;
        } else { out += static_cast<char>(c); }
    }
    out += '"';
    return out;
}

static std::string sha1_to_hex(const lt::sha1_hash& h) {
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    for (auto c : h) {
        oss << std::setw(2) << static_cast<unsigned int>(static_cast<unsigned char>(c));
    }
    return oss.str();
}

static lt::torrent_handle find_by_hash(const std::string& hex) {
    if (!g_session) return {};
    for (auto& h : g_session->get_torrents()) {
        if (h.is_valid() && sha1_to_hex(h.status().info_hashes.v1) == hex)
            return h;
    }
    return {};
}

// ── resume data persistence ───────────────────────────────────────────────

static std::string resume_path(const lt::sha1_hash& hash) {
    return g_state_dir + "/" + sha1_to_hex(hash) + ".resume";
}

static void load_resume_data() {
    mkdir(g_state_dir.c_str(), 0755);
    DIR* dir = opendir(g_state_dir.c_str());
    if (!dir) return;

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string fname(entry->d_name);
        if (fname.size() < 7 || fname.substr(fname.size() - 7) != ".resume") continue;

        std::string path = g_state_dir + "/" + fname;
        std::ifstream f(path, std::ios::binary);
        if (!f) continue;

        std::vector<char> buf(std::istreambuf_iterator<char>(f), {});
        lt::error_code ec;
        lt::add_torrent_params params = lt::read_resume_data(buf, ec);
        if (ec) {
            LOGE("Bad resume file %s: %s", fname.c_str(), ec.message().c_str());
            continue;
        }
        // Preserve the save_path from resume data so seeders keep their original path.
        // Only fall back to g_save_path if the resume data has no path set.
        if (params.save_path.empty()) params.save_path = g_save_path;
        g_session->async_add_torrent(params);
        LOGI("Restored torrent from %s  save_path=%s", fname.c_str(), params.save_path.c_str());
    }
    closedir(dir);
}

static void save_all_resume_data() {
    if (!g_session || g_state_dir.empty()) return;
    mkdir(g_state_dir.c_str(), 0755);

    int outstanding = 0;
    for (auto& h : g_session->get_torrents()) {
        if (h.is_valid()) {
            h.save_resume_data(lt::torrent_handle::save_info_dict);
            ++outstanding;
        }
    }

    auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
    while (outstanding > 0 && std::chrono::steady_clock::now() < deadline) {
        g_session->wait_for_alert(std::chrono::milliseconds(200));
        std::vector<lt::alert*> alerts;
        g_session->pop_alerts(&alerts);
        for (auto* a : alerts) {
            if (auto* rd = lt::alert_cast<lt::save_resume_data_alert>(a)) {
                auto buf = lt::write_resume_data_buf(rd->params);
                std::string path = resume_path(rd->params.info_hashes.v1);
                std::ofstream f(path, std::ios::binary | std::ios::trunc);
                f.write(buf.data(), static_cast<std::streamsize>(buf.size()));
                LOGI("Saved resume: %s", path.c_str());
                --outstanding;
            } else if (lt::alert_cast<lt::save_resume_data_failed_alert>(a)) {
                --outstanding;
            }
        }
    }
}

// ── JNI ──────────────────────────────────────────────────────────────────────

extern "C" {

JNIEXPORT void JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jstring savePath, jstring statePath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_session) return;

    const char* sp = env->GetStringUTFChars(savePath, nullptr);
    g_save_path = sp;
    env->ReleaseStringUTFChars(savePath, sp);

    const char* sd = env->GetStringUTFChars(statePath, nullptr);
    g_state_dir = sd;
    env->ReleaseStringUTFChars(statePath, sd);

    lt::settings_pack settings;
    settings.set_int(lt::settings_pack::alert_mask,
        lt::alert::status_notification  |
        lt::alert::error_notification   |
        lt::alert::storage_notification);   // needed for save_resume_data_alert
    settings.set_bool(lt::settings_pack::enable_dht, true);
    settings.set_bool(lt::settings_pack::enable_lsd, true);   // local peer discovery on same LAN
    settings.set_bool(lt::settings_pack::enable_upnp, true);
    settings.set_bool(lt::settings_pack::enable_natpmp, true);
    // Default LSD interval is 5 minutes — far too slow for local testing.
    // 15 s means peers on the same LAN (or same emulator host) find each other almost instantly.
    settings.set_int(lt::settings_pack::local_service_announce_interval, 15);

    g_session = std::make_unique<lt::session>(settings);
    LOGI("Session started  save_path=%s  state_dir=%s", g_save_path.c_str(), g_state_dir.c_str());

    load_resume_data();
}

JNIEXPORT void JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_nativeRelease(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_session) return;
    // Resume data is already saved by nativeSaveResumeData() from onStop.
    // Calling save_all_resume_data() here would block the main thread → ANR.
    g_session.reset();
    LOGI("Session released");
}

// Save resume data without destroying the session (safe to call from onStop)
JNIEXPORT void JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_nativeSaveResumeData(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    save_all_resume_data();
}

JNIEXPORT jstring JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_addMagnet(
        JNIEnv* env, jobject /*thiz*/, jstring magnetUri) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_session) return env->NewStringUTF("error: session not initialized");

    const char* raw = env->GetStringUTFChars(magnetUri, nullptr);
    std::string uri(raw);
    env->ReleaseStringUTFChars(magnetUri, raw);

    lt::error_code ec;
    lt::add_torrent_params params = lt::parse_magnet_uri(uri, ec);
    if (ec) {
        LOGE("Bad magnet URI: %s", ec.message().c_str());
        return env->NewStringUTF(("error: " + ec.message()).c_str());
    }
    params.save_path = g_save_path;
    g_session->async_add_torrent(params);
    LOGI("Queued: %s", uri.c_str());
    return env->NewStringUTF("ok");
}

JNIEXPORT void JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_pauseTorrent(
        JNIEnv* env, jobject /*thiz*/, jstring infoHash) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char* raw = env->GetStringUTFChars(infoHash, nullptr);
    auto h = find_by_hash(raw);
    env->ReleaseStringUTFChars(infoHash, raw);
    if (h.is_valid()) h.pause();
}

JNIEXPORT void JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_resumeTorrent(
        JNIEnv* env, jobject /*thiz*/, jstring infoHash) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char* raw = env->GetStringUTFChars(infoHash, nullptr);
    auto h = find_by_hash(raw);
    env->ReleaseStringUTFChars(infoHash, raw);
    if (h.is_valid()) h.resume();
}

JNIEXPORT void JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_removeTorrent(
        JNIEnv* env, jobject /*thiz*/, jstring infoHash, jboolean deleteFiles) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char* raw = env->GetStringUTFChars(infoHash, nullptr);
    std::string hex(raw);
    env->ReleaseStringUTFChars(infoHash, raw);
    auto h = find_by_hash(hex);
    if (!h.is_valid()) return;
    g_session->remove_torrent(h, deleteFiles
        ? lt::session_handle::delete_files
        : lt::remove_flags_t{});
    // Also delete the resume file so it isn't reloaded on next start
    std::string path = g_state_dir + "/" + hex + ".resume";
    ::remove(path.c_str());
    LOGI("Removed: %s deleteFiles=%d", hex.c_str(), (int)deleteFiles);
}

JNIEXPORT jstring JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_getTorrentsJson(
        JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_session) return env->NewStringUTF("[]");

    std::vector<lt::alert*> alerts;
    g_session->pop_alerts(&alerts);

    std::ostringstream json;
    json << "[";
    bool first = true;
    for (auto& h : g_session->get_torrents()) {
        if (!h.is_valid()) continue;
        lt::torrent_status st = h.status();
        std::string name = st.name.empty() ? sha1_to_hex(st.info_hashes.v1) : st.name;
        bool paused = !!(st.flags & lt::torrent_flags::paused);

        if (!first) json << ",";
        first = false;

        int64_t eta_secs = -1;
        if (!paused && st.state == lt::torrent_status::downloading && st.download_rate > 0) {
            int64_t remaining = st.total_wanted - st.total_wanted_done;
            if (remaining > 0) eta_secs = remaining / st.download_rate;
            else eta_secs = 0;
        }

        std::vector<lt::peer_info> peer_vec;
        h.get_peer_info(peer_vec);

        // Build piece map: 200 buckets, '0'=missing '1'=have '2'=active download
        const int BUCKETS = 200;
        std::string pieces_map(BUCKETS, '0');
        const auto& bf      = st.pieces;
        const int   n_pieces = static_cast<int>(bf.size());
        if (n_pieces > 0) {
            for (int b = 0; b < BUCKETS; ++b) {
                int p0 = (b * n_pieces) / BUCKETS;
                int p1 = std::max(p0 + 1, ((b + 1) * n_pieces) / BUCKETS);
                if (p1 > n_pieces) p1 = n_pieces;
                for (int p = p0; p < p1; ++p) {
                    if (bf[lt::piece_index_t{p}]) { pieces_map[b] = '1'; break; }
                }
            }
            for (const auto& peer : peer_vec) {
                int pi = static_cast<int>(peer.downloading_piece_index);
                if (pi >= 0 && pi < n_pieces) {
                    int b = static_cast<int>(int64_t(pi) * BUCKETS / n_pieces);
                    if (b < BUCKETS && pieces_map[b] == '0') pieces_map[b] = '2';
                }
            }
        }

        // Sort peers by download speed for the peer list display
        std::sort(peer_vec.begin(), peer_vec.end(),
            [](const lt::peer_info& a, const lt::peer_info& b) {
                return a.down_speed > b.down_speed;
            });
        auto top = std::min(peer_vec.size(), size_t(5));

        json << "{"
             << "\"infoHash\":"   << json_str(sha1_to_hex(st.info_hashes.v1)) << ","
             << "\"name\":"       << json_str(name)                  << ","
             << "\"state\":"      << json_str(state_str(st.state))   << ","
             << "\"isPaused\":"   << (paused ? "true" : "false")     << ","
             << "\"progress\":"   << st.progress                     << ","
             << "\"dlRateKbs\":"  << (st.download_rate / 1024)       << ","
             << "\"ulRateKbs\":"  << (st.upload_rate   / 1024)       << ","
             << "\"peers\":"            << st.num_peers                    << ","
             << "\"totalWantedBytes\":" << (int64_t)st.total_wanted        << ","
             << "\"totalDoneBytes\":"   << (int64_t)st.total_wanted_done   << ","
             << "\"etaSecs\":"          << eta_secs                        << ","
             << "\"piecesMap\":"        << json_str(pieces_map)            << ","
             << "\"peerList\":[";
        for (size_t i = 0; i < top; ++i) {
            if (i > 0) json << ",";
            const auto& p = peer_vec[i];
            std::string addr = p.ip.address().to_string()
                             + ":" + std::to_string(p.ip.port());
            json << "{"
                 << "\"ip\":"       << json_str(addr)          << ","
                 << "\"dlKbs\":"    << (p.down_speed / 1024)   << ","
                 << "\"ulKbs\":"    << (p.up_speed   / 1024)   << ","
                 << "\"progress\":" << p.progress
                 << "}";
        }
        json << "],"
             << "\"allTimeUploadBytes\":"   << st.all_time_upload   << ","
             << "\"allTimeDownloadBytes\":" << st.all_time_download  << ","
             << "\"fileList\":[";
        {
            auto ti = h.torrent_file();
            if (ti && ti->num_files() > 0) {
                std::vector<int64_t> fp;
                h.file_progress(fp);
                const auto& fs = ti->files();
                for (int fi = 0; fi < fs.num_files(); ++fi) {
                    if (fi > 0) json << ",";
                    std::string fname = fs.file_path(lt::file_index_t{fi});
                    int64_t fsize = fs.file_size(lt::file_index_t{fi});
                    int64_t fdone = (fi < static_cast<int>(fp.size())) ? fp[fi] : 0;
                    json << "{"
                         << "\"name\":" << json_str(fname) << ","
                         << "\"size\":" << fsize           << ","
                         << "\"done\":" << fdone
                         << "}";
                }
            }
        }
        json << "]}";
    }
    json << "]";
    return env->NewStringUTF(json.str().c_str());
}

JNIEXPORT void JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_setSequentialDownload(
        JNIEnv* env, jobject /*thiz*/, jstring infoHash, jboolean enabled) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char* raw = env->GetStringUTFChars(infoHash, nullptr);
    std::string hex(raw);
    env->ReleaseStringUTFChars(infoHash, raw);
    auto h = find_by_hash(hex);
    if (h.is_valid()) {
        h.set_sequential_download(enabled);
        LOGI("Sequential download %s: %s", enabled ? "enabled" : "disabled", hex.c_str());
    }
}

// Add a torrent from a .torrent file on disk.
// Returns "ok:<infohash>" or "error:<message>".
JNIEXPORT jstring JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_addTorrentFile(
        JNIEnv* env, jobject /*thiz*/, jstring torrentPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_session) return env->NewStringUTF("error: session not initialized");

    const char* path_c = env->GetStringUTFChars(torrentPath, nullptr);
    std::string path(path_c);
    env->ReleaseStringUTFChars(torrentPath, path_c);

    std::ifstream f(path, std::ios::binary);
    if (!f) return env->NewStringUTF(("error: cannot open " + path).c_str());
    std::vector<char> buf(std::istreambuf_iterator<char>(f), {});

    lt::error_code ec;
    lt::torrent_info ti(buf.data(), static_cast<int>(buf.size()), ec);
    if (ec) return env->NewStringUTF(("error: " + ec.message()).c_str());

    lt::add_torrent_params atp;
    atp.ti        = std::make_shared<lt::torrent_info>(ti);
    atp.save_path = g_save_path;
    g_session->async_add_torrent(atp);

    std::string hash = sha1_to_hex(ti.info_hashes().v1);
    LOGI("Added torrent file: %s  hash=%s", path.c_str(), hash.c_str());
    return env->NewStringUTF(("ok:" + hash).c_str());
}

// Create a .torrent from a file or folder and seed it.
// Returns JSON: {"name":"...","torrentFile":"...","magnetUri":"..."} or {"error":"..."}
JNIEXPORT jstring JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_createTorrent(
        JNIEnv* env, jobject /*thiz*/, jstring sourcePath, jstring outputDir) {
    const char* src_c = env->GetStringUTFChars(sourcePath, nullptr);
    const char* out_c = env->GetStringUTFChars(outputDir,  nullptr);
    std::string src(src_c);
    std::string out(out_c);
    env->ReleaseStringUTFChars(sourcePath, src_c);
    env->ReleaseStringUTFChars(outputDir,  out_c);

    auto err = [&](const std::string& msg) -> jstring {
        LOGE("createTorrent: %s", msg.c_str());
        return env->NewStringUTF(("{\"error\":" + json_str(msg) + "}").c_str());
    };

    // Derive parent directory and leaf name from source path
    std::string parent = src;
    std::string leaf   = src;
    auto slash = src.rfind('/');
    if (slash != std::string::npos) {
        parent = src.substr(0, slash);
        leaf   = src.substr(slash + 1);
    }

    lt::file_storage fs;
    lt::add_files(fs, src);
    if (fs.num_files() == 0)
        return err("no files found at: " + src);

    lt::create_torrent ct(fs);
    ct.set_creator("SimpleTorrent/2.0");

    // Public trackers improve peer discovery for torrents shared outside local network
    ct.add_tracker("udp://tracker.opentrackr.org:1337/announce");
    ct.add_tracker("udp://open.stealth.si:80/announce");
    ct.add_tracker("udp://tracker.torrent.eu.org:451/announce");

    lt::error_code ec;
    lt::set_piece_hashes(ct, parent, ec);
    if (ec) return err("set_piece_hashes: " + ec.message());

    // Bencode into memory
    std::vector<char> buf;
    lt::bencode(std::back_inserter(buf), ct.generate());

    // Write .torrent file
    mkdir(out.c_str(), 0755);
    std::string torrent_path = out + "/" + leaf + ".torrent";
    {
        std::ofstream f(torrent_path, std::ios::binary | std::ios::trunc);
        if (!f) return err("cannot write: " + torrent_path);
        f.write(buf.data(), static_cast<std::streamsize>(buf.size()));
    }
    LOGI("Created torrent: %s", torrent_path.c_str());

    // Parse to get info hash and magnet URI
    lt::torrent_info ti(buf.data(), static_cast<int>(buf.size()), ec);
    if (ec) return err("parse torrent: " + ec.message());
    std::string magnet = lt::make_magnet_uri(ti);

    // Seed it immediately
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (g_session) {
            lt::add_torrent_params atp;
            atp.ti        = std::make_shared<lt::torrent_info>(ti);
            atp.save_path = parent;
            atp.flags    |= lt::torrent_flags::seed_mode;
            g_session->async_add_torrent(atp);
        }
    }

    std::string result =
        "{\"name\":"        + json_str(leaf)         + ","
        "\"torrentFile\":"  + json_str(torrent_path) + ","
        "\"magnetUri\":"    + json_str(magnet)        + "}";
    return env->NewStringUTF(result.c_str());
}

// Return the magnet URI for an existing torrent by info-hash, or "" if not found.
JNIEXPORT jstring JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_getMagnetUri(
        JNIEnv* env, jobject /*thiz*/, jstring infoHash) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char* raw = env->GetStringUTFChars(infoHash, nullptr);
    auto h = find_by_hash(raw);
    env->ReleaseStringUTFChars(infoHash, raw);
    if (!h.is_valid()) return env->NewStringUTF("");
    return env->NewStringUTF(lt::make_magnet_uri(h).c_str());
}

// Write a .torrent file for the given info-hash into outputDir.
// Returns the absolute path to the written file, or "" on failure (e.g. metadata not yet fetched).
JNIEXPORT jstring JNICALL
Java_com_jpcottin_simpletorrent_data_TorrentManager_saveTorrentFile(
        JNIEnv* env, jobject /*thiz*/, jstring infoHash, jstring outputDir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char* hash_c = env->GetStringUTFChars(infoHash, nullptr);
    const char* out_c  = env->GetStringUTFChars(outputDir,  nullptr);
    std::string hash(hash_c);
    std::string out(out_c);
    env->ReleaseStringUTFChars(infoHash, hash_c);
    env->ReleaseStringUTFChars(outputDir,  out_c);

    auto h = find_by_hash(hash);
    if (!h.is_valid()) return env->NewStringUTF("");

    auto ti = h.torrent_file();
    if (!ti) return env->NewStringUTF("");   // still fetching metadata

    lt::create_torrent ct(*ti);
    std::vector<char> buf;
    lt::bencode(std::back_inserter(buf), ct.generate());

    mkdir(out.c_str(), 0755);
    std::string path = out + "/" + ti->name() + ".torrent";
    {
        std::ofstream f(path, std::ios::binary | std::ios::trunc);
        if (!f) return env->NewStringUTF("");
        f.write(buf.data(), static_cast<std::streamsize>(buf.size()));
    }
    LOGI("Saved torrent file: %s", path.c_str());
    return env->NewStringUTF(path.c_str());
}

} // extern "C"
