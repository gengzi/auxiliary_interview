import tkinter as tk

from win32_utils import get_virtual_screen_bounds, set_display_affinity, is_windows


class SelectionWindow:
    def __init__(self, root):
        self._root = root
        self._start = None
        self._end = None
        self._selection = None

        self._window = tk.Toplevel(root)
        self._window.withdraw()
        self._window.overrideredirect(True)
        self._window.attributes("-topmost", True)
        self._window.configure(bg="#000000")
        self._window.attributes("-alpha", 0.15)

        x, y, w, h = self._get_bounds()
        self._window.geometry(f"{w}x{h}+{x}+{y}")

        self._canvas = tk.Canvas(self._window, highlightthickness=0, bg="#000000")
        self._canvas.pack(fill=tk.BOTH, expand=True)

        self._rect_id = None
        self._canvas.bind("<ButtonPress-1>", self._on_press)
        self._canvas.bind("<B1-Motion>", self._on_drag)
        self._canvas.bind("<ButtonRelease-1>", self._on_release)

    def show(self):
        self._window.deiconify()
        self._window.grab_set()
        if is_windows():
            set_display_affinity(self._window.winfo_id(), True)
        self._window.wait_window()
        return self._selection

    def _get_bounds(self):
        if is_windows():
            x, y, w, h = get_virtual_screen_bounds()
            if w > 0 and h > 0:
                return x, y, w, h
        return (0, 0, self._root.winfo_screenwidth(), self._root.winfo_screenheight())

    def _on_press(self, event):
        self._start = (event.x_root, event.y_root)
        self._end = self._start
        self._draw()

    def _on_drag(self, event):
        self._end = (event.x_root, event.y_root)
        self._draw()

    def _on_release(self, event):
        self._end = (event.x_root, event.y_root)
        self._selection = _build_rect(self._start, self._end)
        self._window.grab_release()
        self._window.destroy()

    def _draw(self):
        if not self._start or not self._end:
            return
        rect = _build_rect(self._start, self._end)
        if self._rect_id:
            self._canvas.delete(self._rect_id)
        x, y, w, h = rect
        self._rect_id = self._canvas.create_rectangle(
            x - self._window.winfo_rootx(),
            y - self._window.winfo_rooty(),
            x - self._window.winfo_rootx() + w,
            y - self._window.winfo_rooty() + h,
            outline="#0096ff",
            width=2,
        )


def _build_rect(a, b):
    if not a or not b:
        return None
    x1, y1 = a
    x2, y2 = b
    x = min(x1, x2)
    y = min(y1, y2)
    w = abs(x1 - x2)
    h = abs(y1 - y2)
    return (x, y, w, h)
