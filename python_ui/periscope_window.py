import tkinter as tk
from PIL import ImageGrab, ImageTk

from win32_utils import find_monitor_by_id, set_display_affinity


DEFAULT_REFRESH_MS = 120


class PeriscopeWindow:
    def __init__(self, root, display_id, refresh_ms, width, height):
        self._root = root
        self._display_id = display_id
        self._refresh_ms = refresh_ms if refresh_ms > 0 else DEFAULT_REFRESH_MS
        self._width = max(240, int(width))
        self._height = max(180, int(height))
        self._running = False
        self._image_id = None
        self._photo = None

        monitor = find_monitor_by_id(display_id)
        bounds = monitor["bounds"] if monitor else (0, 0, root.winfo_screenwidth(), root.winfo_screenheight())
        self._capture_bounds = bounds

        self._window = tk.Toplevel(root)
        self._window.withdraw()
        self._window.title("Periscope")
        self._window.attributes("-topmost", True)
        self._window.geometry(f"{self._width}x{self._height}")
        self._window.protocol("WM_DELETE_WINDOW", self.stop)

        self._canvas = tk.Canvas(self._window, bg="#000000", highlightthickness=0)
        self._canvas.pack(fill=tk.BOTH, expand=True)

    def start(self):
        if self._running:
            return
        self._running = True
        self._window.deiconify()
        set_display_affinity(self._window.winfo_id(), True)
        self._schedule()

    def stop(self):
        if not self._running:
            return
        self._running = False
        self._window.withdraw()

    def _schedule(self):
        if not self._running:
            return
        self._capture()
        self._window.after(self._refresh_ms, self._schedule)

    def _capture(self):
        if not self._running:
            return
        left, top, right, bottom = self._capture_bounds
        try:
            shot = ImageGrab.grab(bbox=(left, top, right, bottom))
        except Exception:
            return
        w = max(1, self._canvas.winfo_width())
        h = max(1, self._canvas.winfo_height())
        shot = shot.resize((w, h))
        self._photo = ImageTk.PhotoImage(shot)
        if self._image_id is None:
            self._image_id = self._canvas.create_image(0, 0, anchor="nw", image=self._photo)
        else:
            self._canvas.itemconfig(self._image_id, image=self._photo)
