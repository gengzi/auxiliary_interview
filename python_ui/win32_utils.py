import ctypes
import ctypes.wintypes
import threading


USER32 = ctypes.windll.user32 if hasattr(ctypes, "windll") else None


def is_windows():
    return USER32 is not None


def set_click_through(hwnd):
    if not is_windows() or not hwnd:
        return
    GWL_EXSTYLE = -20
    WS_EX_LAYERED = 0x00080000
    WS_EX_TRANSPARENT = 0x00000020
    WS_EX_TOOLWINDOW = 0x00000080
    WS_EX_COMPOSITED = 0x02000000
    try:
        ex_style = USER32.GetWindowLongW(hwnd, GWL_EXSTYLE)
        ex_style |= WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW | WS_EX_COMPOSITED
        USER32.SetWindowLongW(hwnd, GWL_EXSTYLE, ex_style)
    except Exception:
        pass


def set_display_affinity(hwnd, exclude):
    if not is_windows() or not hwnd:
        return
    WDA_EXCLUDEFROMCAPTURE = 0x00000011
    WDA_MONITOR = 0x00000001
    WDA_NONE = 0x00000000
    try:
        affinity = WDA_EXCLUDEFROMCAPTURE if exclude else WDA_NONE
        ok = USER32.SetWindowDisplayAffinity(hwnd, affinity)
        if not ok and exclude:
            USER32.SetWindowDisplayAffinity(hwnd, WDA_MONITOR)
    except Exception:
        pass


def get_virtual_screen_bounds():
    if not is_windows():
        return (0, 0, 0, 0)
    SM_XVIRTUALSCREEN = 76
    SM_YVIRTUALSCREEN = 77
    SM_CXVIRTUALSCREEN = 78
    SM_CYVIRTUALSCREEN = 79
    x = USER32.GetSystemMetrics(SM_XVIRTUALSCREEN)
    y = USER32.GetSystemMetrics(SM_YVIRTUALSCREEN)
    w = USER32.GetSystemMetrics(SM_CXVIRTUALSCREEN)
    h = USER32.GetSystemMetrics(SM_CYVIRTUALSCREEN)
    return (x, y, w, h)


def list_monitors():
    monitors = []
    if not is_windows():
        return monitors

    class MONITORINFOEXW(ctypes.Structure):
        _fields_ = [
            ("cbSize", ctypes.wintypes.DWORD),
            ("rcMonitor", ctypes.wintypes.RECT),
            ("rcWork", ctypes.wintypes.RECT),
            ("dwFlags", ctypes.wintypes.DWORD),
            ("szDevice", ctypes.c_wchar * 32),
        ]

    def _callback(h_monitor, hdc, lprc, lparam):
        info = MONITORINFOEXW()
        info.cbSize = ctypes.sizeof(info)
        if USER32.GetMonitorInfoW(h_monitor, ctypes.byref(info)):
            rect = info.rcMonitor
            work = info.rcWork
            monitors.append({
                "id": info.szDevice,
                "bounds": (rect.left, rect.top, rect.right, rect.bottom),
                "work": (work.left, work.top, work.right, work.bottom),
                "primary": bool(info.dwFlags & 1),
            })
        return 1

    MonitorEnumProc = ctypes.WINFUNCTYPE(ctypes.c_int, ctypes.wintypes.HMONITOR,
                                         ctypes.wintypes.HDC, ctypes.POINTER(ctypes.wintypes.RECT),
                                         ctypes.wintypes.LPARAM)
    USER32.EnumDisplayMonitors(0, 0, MonitorEnumProc(_callback), 0)
    return monitors


def find_monitor_by_id(display_id):
    monitors = list_monitors()
    if not monitors:
        return None
    if not display_id:
        for mon in monitors:
            if mon.get("primary"):
                return mon
        return monitors[0]
    needle = display_id.strip().lower()
    for mon in monitors:
        mon_id = (mon.get("id") or "").lower()
        if needle in mon_id:
            return mon
    return monitors[0]


class GlobalHotkey:
    WM_HOTKEY = 0x0312
    MOD_CONTROL = 0x0002

    def __init__(self, key_vk, callback):
        self._key_vk = key_vk
        self._callback = callback
        self._thread = None
        self._stop = threading.Event()
        self._registered = False
        self._hotkey_id = 1

    def start(self):
        if not is_windows() or self._thread:
            return
        self._thread = threading.Thread(target=self._run, name="global-hotkey", daemon=True)
        self._thread.start()

    def stop(self):
        if not is_windows() or not self._thread:
            return
        self._stop.set()
        try:
            USER32.PostThreadMessageW(self._thread.ident, 0x0012, 0, 0)
        except Exception:
            pass

    def _run(self):
        if not USER32.RegisterHotKey(None, self._hotkey_id, self.MOD_CONTROL, self._key_vk):
            return
        self._registered = True
        msg = ctypes.wintypes.MSG()
        while not self._stop.is_set():
            res = USER32.GetMessageW(ctypes.byref(msg), None, 0, 0)
            if res == 0:
                break
            if msg.message == self.WM_HOTKEY:
                try:
                    self._callback()
                except Exception:
                    pass
        if self._registered:
            USER32.UnregisterHotKey(None, self._hotkey_id)
