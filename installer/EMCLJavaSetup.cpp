// EMCLJavaSetup.cpp
// Optional dependency bootstrap for EMCL.jar users without Java.
// Downloads the Temurin JRE 17 MSI, retries on truncated downloads,
// installs silently, then informs the user and deletes itself.
//
// Build (Windows, MSVC x64):
//   cl /EHsc /O2 /D_UNICODE /DUNICODE EMCLJavaSetup.cpp /link /OUT:EMCLJavaSetup.exe urlmon.lib
#include <windows.h>
#include <urlmon.h>
#include <string>
#include <iostream>

#pragma comment(lib, "urlmon.lib")

static const wchar_t* JRE_URL =
    L"https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse";
static const wchar_t* MSI_PATH = L"EMCLJre17Setup.msi";
static const unsigned long long MIN_MSI_BYTES = 20ULL * 1024ULL * 1024ULL;
static const int MAX_ATTEMPTS = 3;

static unsigned long long FileSize(const wchar_t* path) {
    WIN32_FILE_ATTRIBUTE_DATA data;
    if (!GetFileAttributesExW(path, GetFileExInfoStandard, &data)) return 0;
    LARGE_INTEGER size;
    size.HighPart = data.nFileSizeHigh;
    size.LowPart = data.nFileSizeLow;
    return static_cast<unsigned long long>(size.QuadPart);
}

static bool DownloadWithRetry() {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; ++attempt) {
        DeleteFileW(MSI_PATH);
        std::wcout << L"[EMCL] Downloading Temurin JRE 17 (attempt " << attempt << L"/" << MAX_ATTEMPTS << L")...\n";
        HRESULT hr = URLDownloadToFileW(nullptr, JRE_URL, MSI_PATH, 0, nullptr);
        if (SUCCEEDED(hr)) {
            unsigned long long size = FileSize(MSI_PATH);
            std::wcout << L"[EMCL] Downloaded size: " << size << L" bytes\n";
            if (size >= MIN_MSI_BYTES) return true;
            std::wcerr << L"[EMCL] Download truncated, retrying...\n";
        } else {
            std::wcerr << L"[EMCL] Download failed (HRESULT 0x" << std::hex << hr << std::dec << L"), retrying...\n";
        }
        Sleep(2000);
    }
    return false;
}

static bool InstallSilently() {
    std::wcout << L"[EMCL] Installing silently...\n";
    std::wstring cmd =
        L"msiexec.exe /i \"" + std::wstring(MSI_PATH) +
        L"\" /quiet /norestart ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome";
    SHELLEXECUTEINFOW info{};
    info.cbSize = sizeof(info);
    info.fMask = SEE_MASK_NOCLOSEPROCESS;
    info.lpVerb = L"runas";
    info.lpFile = L"msiexec.exe";
    info.lpParameters = cmd.c_str() + wcslen(L"msiexec.exe ");
    info.nShow = SW_SHOWNORMAL;
    if (!ShellExecuteExW(&info) || info.hProcess == nullptr) {
        // Fall back without elevation request.
        return reinterpret_cast<INT_PTR>(ShellExecuteW(nullptr, L"open", L"msiexec.exe",
            cmd.c_str() + wcslen(L"msiexec.exe "), nullptr, SW_SHOWNORMAL)) > 32;
    }
    WaitForSingleObject(info.hProcess, INFINITE);
    DWORD code = 1;
    GetExitCodeProcess(info.hProcess, &code);
    CloseHandle(info.hProcess);
    return code == 0 || code == 1638 || code == 3010;
}

static std::string NarrowAscii(const wchar_t* w) {
    std::string out;
    for (; *w; ++w) out.push_back(static_cast<char>(*w & 0x7F));
    return out;
}

static void ScheduleSelfDelete() {
    wchar_t exePathW[MAX_PATH];
    GetModuleFileNameW(nullptr, exePathW, MAX_PATH);
    std::string exePath = NarrowAscii(exePathW);
    std::string msi = NarrowAscii(MSI_PATH);
    std::string bat =
        "@echo off\r\nping 127.0.0.1 -n 4 >nul\r\ndel /f /q \"" + exePath +
        "\"\r\ndel /f /q \"" + msi + "\"\r\n";
    wchar_t tempPathW[MAX_PATH];
    GetTempPathW(MAX_PATH, tempPathW);
    std::string tempPath = NarrowAscii(tempPathW);
    std::string batPath = tempPath + "emcl_cleanup.bat";
    HANDLE f = CreateFileA(batPath.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (f != INVALID_HANDLE_VALUE) {
        DWORD written;
        WriteFile(f, bat.data(), static_cast<DWORD>(bat.size()), &written, nullptr);
        CloseHandle(f);
        std::string cmd = "/c \"" + batPath + "\"";
        ShellExecuteA(nullptr, "open", "cmd.exe", cmd.c_str(), nullptr, SW_HIDE);
    }
}

int wmain() {
    SetConsoleOutputCP(CP_UTF8);
    std::wcout << L"EMCL Java Setup (optional)\n";
    std::wcout << L"This will download and silently install Temurin JRE 17.\n";

    if (!DownloadWithRetry()) {
        MessageBoxW(nullptr,
            L"JRE download failed after multiple retries.\n"
            L"Please install Java 17 manually, or use EMCL-windows-portable.zip.",
            L"EMCL Setup", MB_OK | MB_ICONERROR);
        return 1;
    }

    if (!InstallSilently()) {
        MessageBoxW(nullptr,
            L"Silent installation did not complete.\n"
            L"Please run the downloaded EMCLJre17Setup.msi manually.",
            L"EMCL Setup", MB_OK | MB_ICONWARNING);
        return 2;
    }

    MessageBoxW(nullptr,
        L"Temurin JRE 17 has been installed.\n"
        L"You can now run EMCL.jar with: java -jar EMCL.jar\n\n"
        L"Click OK and this setup will clean itself up.",
        L"EMCL Setup", MB_OK | MB_ICONINFORMATION);

    DeleteFileW(MSI_PATH);
    ScheduleSelfDelete();
    return 0;
}
