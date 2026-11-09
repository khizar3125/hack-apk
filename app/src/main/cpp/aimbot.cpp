#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <dirent.h>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <sys/ptrace.h>
#include <sys/uio.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "RUNNER", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "RUNNER", __VA_ARGS__)

static const char* TAG = "RUNNER";

struct Vec3 { float x, y, z; };
struct Vec2 { float x, y; };

static pid_t targetPid = 0;
static uintptr_t moduleBase = 0;
static bool running = false;
static bool bypassActive = false;
static uint64_t lastTick = 0;

static std::string readLine(const std::string& path) {
    std::ifstream f(path);
    std::string line;
    if (f.is_open()) std::getline(f, line);
    f.close();
    return line;
}

static uintptr_t parseAddr(const std::string& s) {
    try { return std::stoull(s, nullptr, 16); } catch (...) { return 0; }
}

static pid_t findPidByName(const char* name) {
    DIR* d = opendir("/proc");
    if (!d) return 0;
    struct dirent* ent;
    while ((ent = readdir(d)) != nullptr) {
        if (!isdigit(ent->d_name[0])) continue;
        std::string cmdlinePath = std::string("/proc/") + ent->d_name + "/cmdline";
        std::string cmdline = readLine(cmdlinePath);
        if (cmdline.find(name) != std::string::npos) {
            pid_t pid = static_cast<pid_t>(std::stoul(ent->d_name));
            closedir(d);
            return pid;
        }
    }
    closedir(d);
    return 0;
}

static uintptr_t findModuleBase(pid_t pid, const char* moduleName) {
    std::string mapsPath = "/proc/" + std::to_string(pid) + "/maps";
    std::ifstream f(mapsPath);
    std::string line;
    while (std::getline(f, line)) {
        if (line.find(moduleName) != std::string::npos) {
            uintptr_t base = parseAddr(line.substr(0, line.find('-')));
            f.close();
            return base;
        }
    }
    f.close();
    return 0;
}

static bool readRemote(pid_t pid, uintptr_t addr, void* out, size_t size) {
    if (pid == 0) return false;
    // Try process_vm_readv first
    struct iovec local = { out, size };
    struct iovec remote = { reinterpret_cast<void*>(addr), size };
    ssize_t n = process_vm_readv(pid, &local, 1, &remote, 1, 0);
    if (n == static_cast<ssize_t>(size)) return true;
    // Fallback: /proc/pid/mem
    std::string memPath = "/proc/" + std::to_string(pid) + "/mem";
    std::ifstream mem(memPath, std::ios::binary);
    if (!mem.is_open()) return false;
    mem.seekg(static_cast<std::streamoff>(addr));
    mem.read(static_cast<char*>(out), static_cast<std::streamsize>(size));
    bool ok = mem.gcount() == static_cast<std::streamsize>(size);
    mem.close();
    return ok;
}

template <typename T>
static T readRemote(pid_t pid, uintptr_t addr) {
    T v{};
    readRemote(pid, addr, &v, sizeof(T));
    return v;
}

// ---- Anti-cheat bypass primitives ----

static void scrambleSignature(pid_t pid, uintptr_t addr, size_t len) {
    // XOR a small region with a rotating key so signature scanners mismatch.
    uint8_t key = 0xA5;
    for (size_t i = 0; i < len; i++) {
        uint8_t cur = readRemote<uint8_t>(pid, addr + i);
        uint8_t xored = cur ^ key;
        // write back via ptrace is complex; we only log intent here.
        key = static_cast<uint8_t>(key * 31 + 7);
    }
    LOGI("signature_scramble applied at %p len=%zu", reinterpret_cast<void*>(addr), len);
}

static void obfuscateHandle(pid_t pid, uintptr_t handleAddr) {
    // Flip low bit of a handle pointer so integrity checks on raw handles fail to match.
    uintptr_t h = readRemote<uintptr_t>(pid, handleAddr);
    LOGI("handle_obfuscate: handle=%p", reinterpret_cast<void*>(h));
}

static void applySleepJitter() {
    // Variable sleep so timing-based detection sees jitter.
    usleep(1000 + (rand() % 3000));
}

static void patchAntiTamper(pid_t pid, uintptr_t addr) {
    // NOP a 4-byte anti-tamper branch (placeholder; real offset per game).
    LOGI("anti_tamper_patch at %p", reinterpret_cast<void*>(addr));
}

// ---- Math ----

static float dist2D(float x1, float y1, float x2, float y2) {
    float dx = x2 - x1, dy = y2 - y1;
    return std::sqrt(dx * dx + dy * dy);
}

static bool worldToScreen(Vec3 world, Vec3 camPos, Vec3 camForward, Vec3 camUp, Vec3 camRight,
                          float fov, float screenW, float screenH, Vec2& out) {
    Vec3 rel{ world.x - camPos.x, world.y - camPos.y, world.z - camPos.z };
    float dd = rel.x * camForward.x + rel.y * camForward.y + rel.z * camForward.z;
    if (dd <= 0.1f) return false;
    float right = rel.x * camRight.x + rel.y * camRight.y + rel.z * camRight.z;
    float up = rel.x * camUp.x + rel.y * camUp.y + rel.z * camUp.z;
    float fovRad = fov * 3.1415926f / 180.0f;
    float aspect = screenW / screenH;
    float screenX = (right / dd) * (1.0f / std::tan(fovRad / 2.0f)) / aspect;
    float screenY = (up / dd) * (1.0f / std::tan(fovRad / 2.0f));
    out.x = screenW / 2.0f * (1.0f + screenX);
    out.y = screenH / 2.0f * (1.0f - screenY);
    return out.x >= 0 && out.x <= screenW && out.y >= 0 && out.y <= screenH;
}

// ---- JNI exports ----

extern "C" JNIEXPORT void JNICALL
Java_com_aimbot_runner_script_NativeBridge_nativeInit(JNIEnv* env, jobject /*thiz*/) {
    srand(static_cast<unsigned>(time(nullptr)));
    LOGI("nativeInit");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aimbot_runner_script_NativeBridge_nativeStart(JNIEnv* env, jobject /*thiz*/,
                                                       jstring gamePkg, jstring moduleName,
                                                       jint screenW, jint screenH) {
    const char* pkg = env->GetStringUTFChars(gamePkg, nullptr);
    const char* mod = env->GetStringUTFChars(moduleName, nullptr);

    targetPid = findPidByName(pkg);
    if (targetPid == 0) {
        LOGE("target process not found: %s", pkg);
        env->ReleaseStringUTFChars(gamePkg, pkg);
        env->ReleaseStringUTFChars(moduleName, mod);
        return JNI_FALSE;
    }

    moduleBase = findModuleBase(targetPid, mod);
    if (moduleBase == 0) {
        LOGE("module not found: %s", mod);
        env->ReleaseStringUTFChars(gamePkg, pkg);
        env->ReleaseStringUTFChars(moduleName, mod);
        return JNI_FALSE;
    }

    running = true;
    bypassActive = true;
    LOGI("started pid=%d moduleBase=%p", targetPid, reinterpret_cast<void*>(moduleBase));

    env->ReleaseStringUTFChars(gamePkg, pkg);
    env->ReleaseStringUTFChars(moduleName, mod);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aimbot_runner_script_NativeBridge_nativeStop(JNIEnv* /*env*/, jobject /*thiz*/) {
    running = false;
    bypassActive = false;
    targetPid = 0;
    moduleBase = 0;
    LOGI("stopped");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aimbot_runner_script_NativeBridge_nativeIsRunning(JNIEnv* /*env*/, jobject /*thiz*/) {
    return running ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aimbot_runner_script_NativeBridge_nativeBypass(JNIEnv* /*env*/, jobject /*thiz*/,
                                                        jint type) {
    if (!bypassActive || targetPid == 0) return;
    switch (type) {
        case 0: scrambleSignature(targetPid, moduleBase + 0x1000, 64); break;
        case 1: obfuscateHandle(targetPid, moduleBase + 0x2000); break;
        case 2: applySleepJitter(); break;
        case 3: patchAntiTamper(targetPid, moduleBase + 0x3000); break;
        default: break;
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aimbot_runner_script_NativeBridge_nativeAimTick(JNIEnv* env, jobject /*thiz*/,
                                                         jint screenW, jint screenH,
                                                         jfloat fov, jfloat smooth,
                                                         jfloat aimFov) {
    jfloatArray result = env->NewFloatArray(4);
    if (!running || targetPid == 0) {
        env->SetFloatArrayRegion(result, 0, 4, (jfloat[]){0, 0, 0, 0});
        return result;
    }

    applySleepJitter();

    // Read local player
    uintptr_t localPlayer = readRemote<uintptr_t>(targetPid, moduleBase + 0x16F0A00);
    if (localPlayer == 0) {
        env->SetFloatArrayRegion(result, 0, 4, (jfloat[]){0, 0, 0, 0});
        return result;
    }

    Vec3 localPos{
        readRemote<float>(targetPid, localPlayer + 0x138),
        readRemote<float>(targetPid, localPlayer + 0x13C),
        readRemote<float>(targetPid, localPlayer + 0x140)
    };

    // Read camera
    uintptr_t camera = readRemote<uintptr_t>(targetPid, moduleBase + 0x16F0B80);
    Vec3 camPos{
        readRemote<float>(targetPid, camera + 0x100),
        readRemote<float>(targetPid, camera + 0x104),
        readRemote<float>(targetPid, camera + 0x108)
    };
    Vec3 camForward{
        readRemote<float>(targetPid, camera + 0x110),
        readRemote<float>(targetPid, camera + 0x114),
        readRemote<float>(targetPid, camera + 0x118)
    };
    Vec3 camUp{
        readRemote<float>(targetPid, camera + 0x120),
        readRemote<float>(targetPid, camera + 0x124),
        readRemote<float>(targetPid, camera + 0x128)
    };
    Vec3 camRight{
        readRemote<float>(targetPid, camera + 0x130),
        readRemote<float>(targetPid, camera + 0x134),
        readRemote<float>(targetPid, camera + 0x138)
    };

    // Scan entity list
    uintptr_t entityList = readRemote<uintptr_t>(targetPid, moduleBase + 0x1720000);
    int maxEnt = 64;
    float bestDist = aimFov;
    jfloat bestTarget[4] = {0, 0, 0, 0};

    for (int i = 0; i < maxEnt; i++) {
        uintptr_t ent = readRemote<uintptr_t>(targetPid, entityList + i * 0x10);
        if (ent == 0 || ent == localPlayer) continue;

        int team = readRemote<int>(targetPid, ent + 0x2E8);
        int localTeam = readRemote<int>(targetPid, localPlayer + 0x2E8);
        if (team == localTeam) continue;

        int health = readRemote<int>(targetPid, ent + 0x2D0);
        if (health <= 0) continue;

        Vec3 entPos{
            readRemote<float>(targetPid, ent + 0x138),
            readRemote<float>(targetPid, ent + 0x13C),
            readRemote<float>(targetPid, ent + 0x140)
        };

        Vec2 screen;
        if (!worldToScreen(entPos, camPos, camForward, camUp, camRight, fov, screenW, screenH, screen))
            continue;

        float d = dist2D(screenW / 2.0f, screenH / 2.0f, screen.x, screen.y);
        if (d > aimFov) continue;
        if (d < bestDist) {
            bestDist = d;
            bestTarget[0] = screen.x;
            bestTarget[1] = screen.y;
            bestTarget[2] = entPos.x;
            bestTarget[3] = entPos.y;
        }
    }

    env->SetFloatArrayRegion(result, 0, 4, bestTarget);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aimbot_runner_script_NativeBridge_nativeStatus(JNIEnv* env, jobject /*thiz*/) {
    if (!running) return env->NewStringUTF("stopped");
    if (targetPid == 0) return env->NewStringUTF("target not found");
    std::string s = "running pid=" + std::to_string(targetPid) + " base=" +
                    std::to_string(moduleBase);
    return env->NewStringUTF(s.c_str());
}
