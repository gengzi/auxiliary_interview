import tkinter as tk

from win32_utils import find_monitor_by_id, set_click_through, set_display_affinity


OVERLAY_GAP = 8
OVERLAY_WIDTH = 420
OVERLAY_MIN_HEIGHT = 200
OVERLAY_MAX_HEIGHT = 1200


class OverlayWindow:
    def __init__(self, root, target_display_id="", exclude_from_capture=True, i18n=None):
        self._root = root
        self._target_display_id = (target_display_id or "").strip().lower()
        self._exclude_from_capture = exclude_from_capture
        self._i18n = i18n
        self._last_region = None

        self._window = tk.Toplevel(root)
        self._window.withdraw()
        self._window.overrideredirect(True)
        self._window.attributes("-topmost", True)
        self._window.configure(bg="#282828")
        self._window.attributes("-alpha", 0.88)
        self._window.bind("<Map>", self._on_map)

        self._frame = tk.Frame(self._window, bg="#282828")
        self._frame.pack(fill=tk.BOTH, expand=True)

        self._scroll = tk.Scrollbar(self._frame)
        self._scroll.pack(side=tk.RIGHT, fill=tk.Y)

        self._text = tk.Text(
            self._frame,
            wrap=tk.WORD,
            height=8,
            width=40,
            bg="#282828",
            fg="#ffffff",
            bd=0,
            highlightthickness=0,
            padx=12,
            pady=12,
            yscrollcommand=self._scroll.set,
        )
        self._text.insert("1.0", self._no_answer_text())
        self._text.config(state=tk.DISABLED)
        self._text.pack(fill=tk.BOTH, expand=True)
        self._scroll.config(command=self._text.yview)

    def _on_map(self, _event=None):
        hwnd = self._window.winfo_id()
        set_click_through(hwnd)
        set_display_affinity(hwnd, self._exclude_from_capture)

    def show(self, region=None):
        self._last_region = region
        target = self._get_target_screen_bounds()
        if region:
            if not _intersects(target, region):
                bounds = self._calculate_overlay_bounds_for_screen(target)
            else:
                bounds = self._calculate_overlay_bounds(region, target)
        elif self._target_display_id:
            bounds = self._calculate_overlay_bounds_for_screen(target)
        else:
            bounds = self._calculate_overlay_bounds_for_screen(target)

        self._apply_bounds(bounds)
        if not self._window.winfo_viewable():
            self._window.deiconify()
        self._window.lift()

    def hide(self):
        self._window.withdraw()

    def is_visible(self):
        return bool(self._window.winfo_viewable())

    def set_target_display_id(self, display_id):
        self._target_display_id = (display_id or "").strip().lower()

    def set_exclude_from_capture(self, exclude):
        self._exclude_from_capture = bool(exclude)
        set_display_affinity(self._window.winfo_id(), self._exclude_from_capture)

    def set_answer(self, text):
        content = text if text else self._no_answer_text()
        self._text.config(state=tk.NORMAL)
        self._text.delete("1.0", tk.END)
        self._text.insert("1.0", content)
        self._text.config(state=tk.DISABLED)
        self._refresh_bounds_for_content()

    def _no_answer_text(self):
        if self._i18n:
            return self._i18n.tr("overlay.no_answer")
        return "No answer yet"

    def _refresh_bounds_for_content(self):
        target = self._get_target_screen_bounds()
        region = self._last_region
        if region:
            if not _intersects(target, region):
                bounds = self._calculate_overlay_bounds_for_screen(target)
            else:
                bounds = self._calculate_overlay_bounds(region, target)
        else:
            bounds = self._calculate_overlay_bounds_for_screen(target)
        self._apply_bounds(bounds)

    def _apply_bounds(self, bounds):
        x, y, w, h = bounds
        self._window.geometry(f"{w}x{h}+{x}+{y}")

    def _get_target_screen_bounds(self):
        monitor = find_monitor_by_id(self._target_display_id)
        if monitor:
            left, top, right, bottom = monitor["work"]
            return (left, top, right - left, bottom - top)
        width = self._root.winfo_screenwidth()
        height = self._root.winfo_screenheight()
        return (0, 0, width, height)

    def _calculate_overlay_bounds(self, region, screen):
        screen_x, screen_y, screen_w, screen_h = screen
        max_width = max(120, min(OVERLAY_WIDTH, screen_w - OVERLAY_GAP * 2))
        preferred_height = self._preferred_height(max_width, screen_h)

        rx, ry, rw, rh = region
        space_right = (screen_x + screen_w) - (rx + rw) - OVERLAY_GAP
        space_left = rx - screen_x - OVERLAY_GAP

        width = max_width
        if space_right >= max_width:
            x = rx + rw + OVERLAY_GAP
        elif space_left >= max_width:
            x = rx - OVERLAY_GAP - max_width
        elif space_right >= space_left and space_right > 0:
            width = max(120, min(max_width, space_right))
            x = rx + rw + OVERLAY_GAP
        elif space_left > 0:
            width = max(120, min(max_width, space_left))
            x = rx - OVERLAY_GAP - width
        else:
            space_above = ry - screen_y - OVERLAY_GAP
            space_below = (screen_y + screen_h) - (ry + rh) - OVERLAY_GAP
            height = min(preferred_height, max(OVERLAY_MIN_HEIGHT, max(space_above, space_below)))
            y = ry + rh + OVERLAY_GAP if space_below >= height else ry - OVERLAY_GAP - height
            fallback_width = min(max_width, screen_w - OVERLAY_GAP * 2)
            fallback_x = _clamp(rx, screen_x + OVERLAY_GAP, screen_x + screen_w - OVERLAY_GAP - fallback_width)
            return (fallback_x, y, fallback_width, height)

        height = min(preferred_height, screen_h - OVERLAY_GAP * 2)
        y = _clamp(ry, screen_y + OVERLAY_GAP, screen_y + screen_h - OVERLAY_GAP - height)
        return (x, y, width, height)

    def _calculate_overlay_bounds_for_screen(self, screen):
        screen_x, screen_y, screen_w, screen_h = screen
        width = max(120, min(OVERLAY_WIDTH, screen_w - OVERLAY_GAP * 2))
        height = self._preferred_height(width, screen_h)
        x = screen_x + screen_w - OVERLAY_GAP - width
        y = screen_y + OVERLAY_GAP
        return (x, y, width, height)

    def _preferred_height(self, width, screen_height):
        self._text.config(width=max(20, int(width / 8)))
        self._text.update_idletasks()
        line_count = int(self._text.index("end-1c").split(".")[0])
        info = self._text.dlineinfo("1.0")
        line_height = max(14, int(info[3]) if info else 16)
        content_height = line_count * line_height + 24
        max_height = min(OVERLAY_MAX_HEIGHT, screen_height - OVERLAY_GAP * 2)
        return max(OVERLAY_MIN_HEIGHT, min(content_height, max_height))


def _intersects(screen, region):
    sx, sy, sw, sh = screen
    rx, ry, rw, rh = region
    return not (rx + rw <= sx or rx >= sx + sw or ry + rh <= sy or ry >= sy + sh)


def _clamp(value, minimum, maximum):
    return max(minimum, min(value, maximum))
