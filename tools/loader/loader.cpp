#include <windows.h>
#include <winhttp.h>
#include <shellapi.h>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <string>
#include <vector>
#include <iostream>

#pragma comment(lib, "winhttp.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "user32.lib")

#define RES_MOD_JAR 101

static const wchar_t* LOADER_VERSION   = L"0.19.3";
static const wchar_t* INSTALLER_VER    = L"1.1.2";
static const wchar_t* MC_VERSION       = L"1.21.4";

static const wchar_t* URL_INSTALLER_HOST = L"maven.fabricmc.net";
static const wchar_t* URL_INSTALLER_PATH = L"/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar";

static const wchar_t* URL_FAPI_HOST = L"cdn.modrinth.com";
static const wchar_t* URL_FAPI_PATH = L"/data/P7dR8mSH/versions/p96k10UR/fabric-api-0.119.4%2B1.21.4.jar";

static const wchar_t* URL_JRE_HOST = L"api.adoptium.net";
static const wchar_t* URL_JRE_PATH = L"/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse";

static HANDLE g_console = NULL;

static void SetColor(WORD c) { if (g_console) SetConsoleTextAttribute(g_console, c); }
static void Print(const wchar_t* s, WORD color = 7) {
    SetColor(color);
    DWORD written;
    WriteConsoleW(g_console, s, (DWORD)wcslen(s), &written, NULL);
}
static void PrintLine(const wchar_t* s = L"", WORD color = 7) {
    Print((std::wstring(s) + L"\r\n").c_str(), color);
}

static std::wstring GetEnv(const wchar_t* var) {
    wchar_t buf[MAX_PATH];
    DWORD n = GetEnvironmentVariableW(var, buf, MAX_PATH);
    return n > 0 && n < MAX_PATH ? std::wstring(buf, n) : L"";
}

static bool FileExists(const std::wstring& p) {
    DWORD a = GetFileAttributesW(p.c_str());
    return a != INVALID_FILE_ATTRIBUTES && !(a & FILE_ATTRIBUTE_DIRECTORY);
}

static bool DirExists(const std::wstring& p) {
    DWORD a = GetFileAttributesW(p.c_str());
    return a != INVALID_FILE_ATTRIBUTES && (a & FILE_ATTRIBUTE_DIRECTORY);
}

static void CreateDirDeep(const std::wstring& p) {
    std::wstring cur;
    for (size_t i = 0; i < p.size(); i++) {
        cur += p[i];
        if (p[i] == L'\\' || i + 1 == p.size()) CreateDirectoryW(cur.c_str(), NULL);
    }
}

struct UrlParts { std::wstring host, path; };
static UrlParts SplitUrlHostPath(const wchar_t* host, const wchar_t* path) { return { host, path }; }

static bool HttpDownload(const wchar_t* host, const wchar_t* path, const std::wstring& outFile,
                         const wchar_t* label) {
    HINTERNET session = WinHttpOpen(L"DuquinLoader/1.0", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
                                    WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!session) return false;
    WinHttpSetTimeouts(session, 15000, 60000, 30000, 600000);

    HINTERNET connect = WinHttpConnect(session, host, INTERNET_DEFAULT_HTTPS_PORT, 0);
    if (!connect) { WinHttpCloseHandle(session); return false; }

    HINTERNET request = WinHttpOpenRequest(connect, L"GET", path, NULL, WINHTTP_NO_REFERER,
                                           WINHTTP_DEFAULT_ACCEPT_TYPES, WINHTTP_FLAG_SECURE);
    if (!request) { WinHttpCloseHandle(connect); WinHttpCloseHandle(session); return false; }

    BOOL ok = WinHttpSendRequest(request, WINHTTP_NO_ADDITIONAL_HEADERS, 0, WINHTTP_NO_REQUEST_DATA, 0, 0, 0)
           && WinHttpReceiveResponse(request, NULL);

    // follow up to 5 redirects manually reported? WinHTTP follows them automatically by default.
    if (!ok) {
        PrintLine(L"[!] Ошибка соединения с сервером загрузки.", FOREGROUND_RED);
        WinHttpCloseHandle(request); WinHttpCloseHandle(connect); WinHttpCloseHandle(session);
        return false;
    }

    DWORD status = 0, size = sizeof(status);
    WinHttpQueryHeaders(request, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                        WINHTTP_HEADER_NAME_BY_INDEX, &status, &size, WINHTTP_NO_HEADER_INDEX);
    if (status != 200) {
        wchar_t msg[128];
        wsprintfW(msg, L"[!] Сервер вернул код %lu для %s\n", status, label);
        PrintLine(msg, FOREGROUND_RED);
        WinHttpCloseHandle(request); WinHttpCloseHandle(connect); WinHttpCloseHandle(session);
        return false;
    }

    DWORD lenLen = sizeof(DWORD), contentLen = 0;
    WinHttpQueryHeaders(request, WINHTTP_QUERY_CONTENT_LENGTH | WINHTTP_QUERY_FLAG_NUMBER,
                        WINHTTP_HEADER_NAME_BY_INDEX, &contentLen, &lenLen, WINHTTP_NO_HEADER_INDEX);

    HANDLE file = CreateFileW(outFile.c_str(), GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        PrintLine(L"[!] Не удалось создать файл.", FOREGROUND_RED);
        WinHttpCloseHandle(request); WinHttpCloseHandle(connect); WinHttpCloseHandle(session);
        return false;
    }

    DWORD totalRead = 0;
    char buffer[65536];
    DWORD downloaded = 0;
    while (true) {
        downloaded = 0;
        if (!WinHttpReadData(request, buffer, sizeof(buffer), &downloaded) || downloaded == 0) break;
        DWORD written = 0;
        WriteFile(file, buffer, downloaded, &written, NULL);
        totalRead += written;
        if (contentLen > 0) {
            int pct = (int)((long long)totalRead * 100 / contentLen);
            wchar_t prog[96];
            wsprintfW(prog, L"\r    [%s] %d%%  (%ld / %ld КБ)   ", label, pct, totalRead / 1024, contentLen / 1024);
            Print(prog, FOREGROUND_GREEN | FOREGROUND_INTENSITY);
        }
    }
    PrintLine(L"", 7);
    CloseHandle(file);
    WinHttpCloseHandle(request);
    WinHttpCloseHandle(connect);
    WinHttpCloseHandle(session);

    if (contentLen > 0 && totalRead < contentLen) {
        PrintLine(L"[!] Загрузка оборвалась.", FOREGROUND_RED);
        DeleteFileW(outFile.c_str());
        return false;
    }
    return true;
}

static bool RunProcessGetOutput(const std::wstring& cmdLine, std::string& out) {
    SECURITY_ATTRIBUTES sa = { sizeof(sa), NULL, TRUE };
    HANDLE readEnd = NULL, writeEnd = NULL;
    if (!CreatePipe(&readEnd, &writeEnd, &sa, 0)) return false;
    SetHandleInformation(readEnd, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOW si = {};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = writeEnd;
    si.hStdError = writeEnd;

    PROCESS_INFORMATION pi = {};
    std::vector<wchar_t> buf(cmdLine.begin(), cmdLine.end());
    buf.push_back(0);

    BOOL created = CreateProcessW(NULL, buf.data(), NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi);
    CloseHandle(writeEnd);
    if (!created) { CloseHandle(readEnd); return false; }

    char chunk[4096];
    DWORD n = 0;
    while (ReadFile(readEnd, chunk, sizeof(chunk), &n, NULL) && n > 0) out.append(chunk, n);

    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 1;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    CloseHandle(readEnd);
    out.push_back(0);
    return code == 0 || !out.empty();
}

// returns major version of `java` found, or 0
static int CheckJavaVersion(const std::wstring& javaPath) {
    std::wstring cmd = L"\"" + javaPath + L"\" -version 2>&1";
    std::string out;
    RunProcessGetOutput(cmd, out);
    const char* v = strstr(out.c_str(), "version \"");
    if (v) {
        int major = atoi(v + 9);
        if (major >= 17) return major;
    }
    return 0;
}

static int DetectSystemJava(std::wstring& javaExe) {
    // 1) Check PATH
    {
        std::string out;
        if (RunProcessGetOutput(L"cmd /c java -version 2>&1", out)) {
            const char* v = strstr(out.c_str(), "version \"");
            if (v) {
                int major = atoi(v + 9);
                if (major >= 17) { javaExe = L"java"; return major; }
            }
        }
    }
    // 2) Check JAVA_HOME
    std::wstring javaHome = GetEnv(L"JAVA_HOME");
    if (!javaHome.empty()) {
        std::wstring jh = javaHome + L"\\bin\\java.exe";
        if (FileExists(jh)) {
            int major = CheckJavaVersion(jh);
            if (major >= 17) { javaExe = jh; return major; }
        }
    }
    // 3) Common install paths
    std::wstring programFiles[] = {
        GetEnv(L"ProgramFiles") + L"\\Java",
        GetEnv(L"ProgramFiles") + L"\\Eclipse Adoptium",
        GetEnv(L"ProgramFiles") + L"\\Microsoft\\jdk-*",
        GetEnv(L"ProgramFiles(x86)") + L"\\Java",
        GetEnv(L"LOCALAPPDATA") + L"\\Programs\\Java",
    };
    for (auto& base : programFiles) {
        if (base.empty()) continue;
        WIN32_FIND_DATAW fd;
        std::wstring pattern = base + L"\\*";
        HANDLE h = FindFirstFileW(pattern.c_str(), &fd);
        if (h == INVALID_HANDLE_VALUE) continue;
        do {
            if (!(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
            if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) continue;
            std::wstring candidate = base + L"\\" + fd.cFileName + L"\\bin\\java.exe";
            if (FileExists(candidate)) {
                int major = CheckJavaVersion(candidate);
                if (major >= 17) { javaExe = candidate; FindClose(h); return major; }
            }
        } while (FindNextFileW(h, &fd));
        FindClose(h);
    }
    // 4) Minecraft Launcher bundled Java
    std::wstring mcRuntime = GetEnv(L"LOCALAPPDATA") + L"\\Programs\\Minecraft Launcher\\runtime\\*";
    {
        WIN32_FIND_DATAW fd;
        HANDLE h = FindFirstFileW(mcRuntime.c_str(), &fd);
        if (h != INVALID_HANDLE_VALUE) {
            do {
                if (!(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
                if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) continue;
                std::wstring base = GetEnv(L"LOCALAPPDATA") + L"\\Programs\\Minecraft Launcher\\runtime\\" + fd.cFileName;
                // search recursively one more level for bin\java.exe
                WIN32_FIND_DATAW fd2;
                std::wstring sub = base + L"\\*";
                HANDLE h2 = FindFirstFileW(sub.c_str(), &fd2);
                if (h2 != INVALID_HANDLE_VALUE) {
                    do {
                        if (!(fd2.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
                        std::wstring candidate = base + L"\\" + fd2.cFileName + L"\\bin\\java.exe";
                        if (FileExists(candidate)) {
                            int major = CheckJavaVersion(candidate);
                            if (major >= 17) { javaExe = candidate; FindClose(h2); FindClose(h); return major; }
                        }
                    } while (FindNextFileW(h2, &fd2));
                    FindClose(h2);
                }
            } while (FindNextFileW(h, &fd));
            FindClose(h);
        }
    }
    return 0;
}

static bool ExtractZipWithPowershell(const std::wstring& zipPath, const std::wstring& destDir) {
    // Try tar.exe first (fast, built into Windows 10+)
    std::wstring tarCmd = L"tar.exe xf \"" + zipPath + L"\" -C \"" + destDir + L"\"";
    std::string dummy;
    bool ok = RunProcessGetOutput(tarCmd, dummy);
    if (ok) return true;
    // Fallback to PowerShell
    std::wstring cmd = L"powershell -NoProfile -ExecutionPolicy Bypass -Command \"Expand-Archive -LiteralPath '"
                     + zipPath + L"' -DestinationPath '" + destDir + L"' -Force\"";
    return RunProcessGetOutput(cmd, dummy);
}

// find bin\java.exe inside extracted runtime dir (zip has root folder like jdk-21.x.y+r-jre)
static bool LocateExtractedJava(const std::wstring& runtimeRoot, std::wstring& javaExe) {
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW((runtimeRoot + L"\\*").c_str(), &fd);
    if (h == INVALID_HANDLE_VALUE) return false;
    std::wstring candidate;
    do {
        if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) continue;
        if (!(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
        std::wstring exe = runtimeRoot + L"\\" + fd.cFileName + L"\\bin\\java.exe";
        if (FileExists(exe)) { candidate = exe; break; }
    } while (FindNextFileW(h, &fd));
    FindClose(h);
    if (candidate.empty()) return false;

    // normalize: move inner folder content up so runtime\bin\java.exe
    std::wstring srcDir = candidate.substr(0, candidate.rfind(L"\\bin"));
    if (srcDir != runtimeRoot) {
        std::vector<wchar_t> from(srcDir.begin(), srcDir.end()); from.push_back(0);
        std::vector<wchar_t> to(runtimeRoot.begin(), runtimeRoot.end()); to.push_back(0);
        MoveFileExW(from.data(), to.data(), MOVEFILE_WRITE_THROUGH | MOVEFILE_COPY_ALLOWED);
        // if move failed (cross-volume), copy instead
        if (!FileExists(runtimeRoot + L"\\bin\\java.exe")) {
            WIN32_FIND_DATAW sfd;
            std::wstring sub = srcDir + L"\\*";
            HANDLE sh = FindFirstFileW(sub.c_str(), &sfd);
            if (sh != INVALID_HANDLE_VALUE) {
                do {
                    if (wcscmp(sfd.cFileName, L".") == 0 || wcscmp(sfd.cFileName, L"..") == 0) continue;
                    std::wstring fromPath = srcDir + L"\\" + sfd.cFileName;
                    std::wstring toPath = runtimeRoot + L"\\" + sfd.cFileName;
                    if (sfd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                        CreateDirectoryW(toPath.c_str(), NULL);
                    } else {
                        CopyFileW(fromPath.c_str(), toPath.c_str(), FALSE);
                    }
                } while (FindNextFileW(sh, &sfd));
                FindClose(sh);
            }
        }
    }
    javaExe = runtimeRoot + L"\\bin\\java.exe";
    return FileExists(javaExe);
}

static bool ExtractEmbeddedModJar(const std::wstring& outFile) {
    HRSRC res = FindResourceW(NULL, MAKEINTRESOURCEW(RES_MOD_JAR), (LPCWSTR)RT_RCDATA);
    if (!res) { PrintLine(L"[!] Встроенный ресурс мода не найден.", FOREGROUND_RED); return false; }
    HGLOBAL loaded = LoadResource(NULL, res);
    if (!loaded) return false;
    DWORD size = SizeofResource(NULL, res);
    const void* data = LockResource(loaded);
    if (!data) return false;

    HANDLE file = CreateFileW(outFile.c_str(), GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) return false;
    DWORD written = 0;
    WriteFile(file, data, size, &written, NULL);
    CloseHandle(file);
    return written == size;
}

static bool EnsureJava(std::wstring& javaExe, const std::wstring& baseDir) {
    // 1) previously installed portable runtime
    std::wstring runtimeRoot = baseDir + L"\\runtime";
    std::wstring portJava = runtimeRoot + L"\\bin\\java.exe";
    if (FileExists(portJava)) {
        javaExe = portJava;
        PrintLine(L"[+] Java найдена (встроенная среда).", FOREGROUND_GREEN);
        return true;
    }
    // 1b) scan runtime subfolders if top-level move failed
    {
        WIN32_FIND_DATAW fd;
        std::wstring sub = runtimeRoot + L"\\*";
        HANDLE h = FindFirstFileW(sub.c_str(), &fd);
        if (h != INVALID_HANDLE_VALUE) {
            do {
                if (!(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
                if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) continue;
                std::wstring exe = runtimeRoot + L"\\" + fd.cFileName + L"\\bin\\java.exe";
                if (FileExists(exe)) { javaExe = exe; FindClose(h); PrintLine(L"[+] Java найдена (встроенная среда).", FOREGROUND_GREEN); return true; }
            } while (FindNextFileW(h, &fd));
            FindClose(h);
        }
    }

    // 2) system java >= 17
    int major = DetectSystemJava(javaExe);
    if (major >= 17) {
        wchar_t msg[160];
        wsprintfW(msg, L"[+] Найдена системная Java %d.", major);
        PrintLine(msg, FOREGROUND_GREEN);
        return true;
    }

    // 3) download portable JRE
    PrintLine(L"[*] Java не найдена. Скачиваю переносимую среду (~50 МБ, один раз)...", FOREGROUND_RED | FOREGROUND_GREEN);
    CreateDirDeep(baseDir);
    std::wstring zipPath = baseDir + L"\\jre21.zip";
    if (!HttpDownload(URL_JRE_HOST, URL_JRE_PATH, zipPath, L"Java")) {
        PrintLine(L"[!] Не удалось скачать Java. Проверьте интернет и запустите снова.", FOREGROUND_RED);
        return false;
    }
    PrintLine(L"[*] Распаковка Java...", 8);
    std::wstring tmpExtract = baseDir + L"\\runtime_tmp";
    CreateDirDeep(tmpExtract);
    if (!ExtractZipWithPowershell(zipPath, tmpExtract)) {
        PrintLine(L"[!] Ошибка распаковки.", FOREGROUND_RED);
        return false;
    }
    DeleteFileW(zipPath.c_str());
    if (!LocateExtractedJava(tmpExtract, javaExe)) {
        PrintLine(L"[!] Не удалось найти java.exe после распаковки.", FOREGROUND_RED);
        return false;
    }
    PrintLine(L"[+] Java установлена!", FOREGROUND_GREEN);
    return true;
}

int wmain(int argc, wchar_t** argv) {
    g_console = GetStdHandle(STD_OUTPUT_HANDLE);

    // Set console window icon from embedded resource
    {
        HWND hwnd = GetConsoleWindow();
        if (hwnd) {
            HICON hIcon = LoadIconW(GetModuleHandleW(NULL), MAKEINTRESOURCEW(1));
            if (hIcon) {
                SendMessageW(hwnd, WM_SETICON, ICON_BIG, (LPARAM)hIcon);
                SendMessageW(hwnd, WM_SETICON, ICON_SMALL, (LPARAM)hIcon);
            }
        }
    }

    bool testMode = false;
    for (int i = 1; i < argc; i++) if (!wcscmp(argv[i], L"--test")) testMode = true;

    SetColor(FOREGROUND_RED | FOREGROUND_INTENSITY);
    PrintLine(L"  ██████╗ ██╗   ██╗ ██████╗ ██╗   ██╗██╗███╗   ██╗", 12);
    PrintLine(L"  ██╔══██╗██║   ██║██╔═══██╗██║   ██║██║████╗  ██║", 12);
    PrintLine(L"  ██║  ██║██║   ██║██║   ██║██║   ██║██║██╔██╗ ██║", 12);
    PrintLine(L"  ██║  ██║██║   ██║██║▄▄ ██║██║   ██║██║██║╚██╗██║", 12);
    PrintLine(L"  ██████╔╝╚██████╔╝╚██████╔╝╚██████╔╝██║██║ ╚████║", 12);
    PrintLine(L"  ╚═════╝  ╚═════╝  ╚══▀▀═╝  ╚═════╝ ╚═╝╚═╝  ╚═══╝", 12);
    PrintLine(L"                 C L I E N T   L O A D E R              ", 12);
    PrintLine();
    PrintLine(L"  Minecraft 1.21.4 + Fabric | https://t.me/duquin_client", 8);
    PrintLine();

    std::wstring baseDir = GetEnv(L"APPDATA") + (testMode ? L"\\duqtest_loader" : L"\\.duquin");
    CreateDirDeep(baseDir);

    // ---------- JAVA ----------
    std::wstring javaExe;
    if (!EnsureJava(javaExe, baseDir)) {
        PrintLine();
        PrintLine(L"Нажмите Enter для выхода...", 8);
        std::wstring dummy; std::getline(std::wcin, dummy);
        return 1;
    }

    // ---------- MINECRAFT DIR ----------
    std::wstring mcDir = GetEnv(L"APPDATA") + L"\\.minecraft";
    if (!testMode && !DirExists(mcDir)) {
        PrintLine(L"[!] Официальный Minecraft Launcher не найден (%APPDATA%\\.minecraft).", FOREGROUND_RED);
        PrintLine(L"[*] Открываю сайт для установки лаунчера...");
        ShellExecuteW(NULL, L"open", L"https://www.minecraft.net/download", NULL, NULL, SW_SHOWNORMAL);
        PrintLine();
        PrintLine(L"Нажмите Enter для выхода...", 8);
        { std::wstring d; std::getline(std::wcin, d); }
        return 1;
    }

    // ---------- FABRIC INSTALL ----------
    if (!testMode) {
        std::wstring fabricProfileJar = mcDir + L"\\versions\\fabric-loader-" + LOADER_VERSION + L"-" + MC_VERSION
                                      + L"\\fabric-loader-" + LOADER_VERSION + L"-" + MC_VERSION + L".jar";
        if (!FileExists(fabricProfileJar)) {
            PrintLine(L"[*] Устанавливаю Fabric Loader в официальный лаунчер...");
            std::wstring installerPath = baseDir + L"\\fabric-installer.jar";
            if (!FileExists(installerPath)) {
                if (!HttpDownload(URL_INSTALLER_HOST, URL_INSTALLER_PATH, installerPath, L"Fabric"))
                    goto fail_step;
            }
            std::wstring cmd = L"\"" + javaExe + L"\" -jar \"" + installerPath + L"\" client -dir \"" + mcDir
                             + L"\" -mcversion " + MC_VERSION + L" -loader " + LOADER_VERSION;
            STARTUPINFOW si = {}; si.cb = sizeof(si);
            PROCESS_INFORMATION pi = {};
            std::vector<wchar_t> cmdbuf(cmd.begin(), cmd.end()); cmdbuf.push_back(0);
            if (!CreateProcessW(NULL, cmdbuf.data(), NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
                PrintLine(L"[!] Не удалось запустить установщик Fabric.", FOREGROUND_RED);
                goto fail_step;
            }
            WaitForSingleObject(pi.hProcess, INFINITE);
            DWORD exitCode = 1;
            GetExitCodeProcess(pi.hProcess, &exitCode);
            CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
            if (exitCode != 0 || !FileExists(fabricProfileJar)) {
                PrintLine(L"[!] Установка Fabric завершилась с ошибкой.", FOREGROUND_RED);
                goto fail_step;
            }
            PrintLine(L"[+] Fabric установлен!", FOREGROUND_GREEN);
        } else {
            PrintLine(L"[+] Fabric уже установлен.", FOREGROUND_GREEN);
        }

        // ---------- MODS ----------
        std::wstring modsDir = mcDir + L"\\mods";
        CreateDirDeep(modsDir);

        PrintLine(L"[*] Устанавливаю Duquin Client в mods...");
        if (!ExtractEmbeddedModJar(modsDir + L"\\duquin-1.21.4.jar")) {
            PrintLine(L"[!] Ошибка записи мода.", FOREGROUND_RED);
            goto fail_step;
        }

        bool fapiPresent = false;
        {
            WIN32_FIND_DATAW fd;
            HANDLE h = FindFirstFileW((modsDir + L"\\fabric-api-*.jar").c_str(), &fd);
            if (h != INVALID_HANDLE_VALUE) { fapiPresent = true; FindClose(h); }
        }
        if (!fapiPresent) {
            PrintLine(L"[*] Скачиваю Fabric API (~2 МБ)...");
            if (!HttpDownload(URL_FAPI_HOST, URL_FAPI_PATH, modsDir + L"\\fabric-api-0.119.4+1.21.4.jar", L"API")) {
                PrintLine(L"[!] Не удалось скачать Fabric API.", FOREGROUND_RED);
                goto fail_step;
            }
        } else {
            PrintLine(L"[+] Fabric API уже на месте.", FOREGROUND_GREEN);
        }
    }

    PrintLine(L"[+] Всё готово!", FOREGROUND_GREEN | FOREGROUND_INTENSITY);

    if (!testMode) {
        PrintLine(L"[*] Запускаю официальный лаунчер — выберите профиль Fabric 1.21.4 и жмите Играть.");
        ShellExecuteW(NULL, L"open", L"minecraft://", NULL, NULL, SW_SHOWNORMAL);
    } else {
        PrintLine(L"[TEST] Режим проверки: загрузка/распаковка работают. .minecraft не тронут.");
    }

    PrintLine();
    PrintLine(L"Нажмите Enter для выхода...", 8);
    {
        std::wstring dummy;
        std::getline(std::wcin, dummy);
    }
    return 0;

fail_step:
    PrintLine();
    PrintLine(L"[!] Установка прервана. Запустите лоадер ещё раз.", FOREGROUND_RED);
    PrintLine();
    {
        std::wstring dummy;
        std::getline(std::wcin, dummy);
    }
    return 1;
}
