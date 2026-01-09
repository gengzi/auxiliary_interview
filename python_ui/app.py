import os
import threading
import tkinter as tk

from PIL import ImageGrab

from backend_client import BackendClient
from config import AppConfig
from i18n import I18n
from ocr_service import OcrService
from overlay_window import OverlayWindow
from periscope_window import PeriscopeWindow
from selection_window import SelectionWindow
from win32_utils import GlobalHotkey, list_monitors, is_windows


VK_P = 0x50


class DesktopApp:
    def __init__(self, root):
        self.root = root
        base_dir = os.path.dirname(os.path.abspath(__file__))
        self.config = AppConfig(os.path.join(base_dir, "app.properties"))
        self.i18n = I18n(base_dir)

        self.region = None
        self.is_processing = False
        self.periscope_window = None

        backend_url = self.config.get("BACKEND_URL", "http://localhost:8080")
        overlay_display_id = self.config.get("OVERLAY_DISPLAY_ID", "")
        overlay_exclude = self.config.get_bool("OVERLAY_EXCLUDE_FROM_CAPTURE", True)
        periscope_display_id = self.config.get("PERISCOPE_DISPLAY_ID", "")
        periscope_refresh_ms = self.config.get_int("PERISCOPE_REFRESH_MS", 120)
        periscope_width = self.config.get_int("PERISCOPE_WINDOW_WIDTH", 640)
        periscope_height = self.config.get_int("PERISCOPE_WINDOW_HEIGHT", 360)

        self.backend = BackendClient(backend_url)
        self.ocr = OcrService(self.backend)
        self.overlay = OverlayWindow(
            root,
            target_display_id=overlay_display_id,
            exclude_from_capture=overlay_exclude,
            i18n=self.i18n,
        )

        self._build_ui()
        self._update_texts()
        self._refresh_display_list()

        if periscope_display_id:
            self._start_periscope(periscope_display_id, periscope_refresh_ms, periscope_width, periscope_height)

        self.hotkey = None
        if is_windows():
            self.hotkey = GlobalHotkey(VK_P, lambda: self.root.after(0, self.capture_and_solve))
            self.hotkey.start()

    def _build_ui(self):
        self.colors = {
            "bg": "#f6f4ef",
            "fg": "#1b1b1b",
            "button": "#e6e1d6",
            "button_active": "#d9d2c6",
            "entry": "#ffffff",
            "border": "#cfc9bf",
        }

        self.root.title(self.i18n.tr("app.title"))
        self.root.geometry("720x460")
        self.root.minsize(680, 420)
        self.root.configure(bg=self.colors["bg"])

        header = tk.Frame(self.root, bg=self.colors["bg"])
        header.pack(fill=tk.X, padx=16, pady=(16, 8))

        self.title_label = tk.Label(
            header,
            font=("Segoe UI", 14, "bold"),
            bg=self.colors["bg"],
            fg=self.colors["fg"],
        )
        self.title_label.pack(side=tk.LEFT)

        language_panel = tk.Frame(header, bg=self.colors["bg"])
        language_panel.pack(side=tk.RIGHT)

        self.language_label = tk.Label(language_panel, bg=self.colors["bg"], fg=self.colors["fg"])
        self.language_label.pack(side=tk.LEFT, padx=(0, 6))

        self.language_var = tk.StringVar(value="zh_CN")
        self.language_menu = tk.OptionMenu(language_panel, self.language_var, "zh_CN", "en_US", command=self._on_language_change)
        self.language_menu.config(
            bg=self.colors["button"],
            fg=self.colors["fg"],
            activebackground=self.colors["button_active"],
            highlightthickness=1,
            highlightbackground=self.colors["border"],
        )
        self.language_menu["menu"].config(bg=self.colors["button"], fg=self.colors["fg"])
        self.language_menu.pack(side=tk.RIGHT)

        self.actions_section = tk.LabelFrame(
            self.root,
            bg=self.colors["bg"],
            fg=self.colors["fg"],
            bd=1,
            relief=tk.GROOVE,
        )
        self.actions_section.pack(fill=tk.X, padx=16, pady=(0, 10))

        actions_grid = tk.Frame(self.actions_section, bg=self.colors["bg"])
        actions_grid.pack(fill=tk.X, padx=12, pady=12)

        self.select_button = self._build_button(actions_grid, self.select_region)
        self.solve_button = self._build_button(actions_grid, self.capture_and_solve)
        self.toggle_overlay_button = self._build_button(actions_grid, self.toggle_overlay)

        self.select_button.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=(0, 8))
        self.solve_button.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=(0, 8))
        self.toggle_overlay_button.pack(side=tk.LEFT, expand=True, fill=tk.X)

        self.display_section = tk.LabelFrame(
            self.root,
            bg=self.colors["bg"],
            fg=self.colors["fg"],
            bd=1,
            relief=tk.GROOVE,
        )
        self.display_section.pack(fill=tk.BOTH, expand=True, padx=16, pady=(0, 10))

        settings_frame = tk.Frame(self.display_section, bg=self.colors["bg"])
        settings_frame.pack(fill=tk.X, padx=12, pady=(12, 8))

        self.overlay_display_label = tk.Label(settings_frame, bg=self.colors["bg"], fg=self.colors["fg"])
        self.periscope_display_label = tk.Label(settings_frame, bg=self.colors["bg"], fg=self.colors["fg"])

        self.exclude_check_var = tk.BooleanVar(value=True)
        self.overlay_exclude_check = tk.Checkbutton(
            settings_frame,
            variable=self.exclude_check_var,
            bg=self.colors["bg"],
            fg=self.colors["fg"],
            activebackground=self.colors["bg"],
        )

        self.overlay_display_var = tk.StringVar(value=self.config.get("OVERLAY_DISPLAY_ID", ""))
        self.overlay_display_entry = tk.Entry(
            settings_frame,
            textvariable=self.overlay_display_var,
            width=18,
            bg=self.colors["entry"],
            fg=self.colors["fg"],
            relief=tk.SOLID,
            highlightthickness=1,
            highlightbackground=self.colors["border"],
        )

        self.periscope_display_var = tk.StringVar(value=self.config.get("PERISCOPE_DISPLAY_ID", ""))
        self.periscope_display_entry = tk.Entry(
            settings_frame,
            textvariable=self.periscope_display_var,
            width=18,
            bg=self.colors["entry"],
            fg=self.colors["fg"],
            relief=tk.SOLID,
            highlightthickness=1,
            highlightbackground=self.colors["border"],
        )

        self.apply_display_button = self._build_button(settings_frame, self.apply_display_settings)
        self.periscope_toggle_button = self._build_button(settings_frame, self.toggle_periscope)

        self.overlay_display_label.grid(row=0, column=0, sticky="w", padx=(0, 6), pady=4)
        self.overlay_display_entry.grid(row=0, column=1, sticky="ew", pady=4)
        self.apply_display_button.grid(row=0, column=2, padx=(8, 0), pady=4)

        tk.Label(settings_frame, text="", bg=self.colors["bg"]).grid(row=1, column=0)
        self.overlay_exclude_check.grid(row=1, column=1, columnspan=2, sticky="w", pady=4)

        self.periscope_display_label.grid(row=2, column=0, sticky="w", padx=(0, 6), pady=4)
        self.periscope_display_entry.grid(row=2, column=1, sticky="ew", pady=4)
        self.periscope_toggle_button.grid(row=2, column=2, padx=(8, 0), pady=4)

        settings_frame.columnconfigure(1, weight=1)

        display_header = tk.Frame(self.display_section, bg=self.colors["bg"])
        display_header.pack(fill=tk.X, padx=12)

        self.display_list_label = tk.Label(display_header, bg=self.colors["bg"], fg=self.colors["fg"])
        self.display_list_label.pack(side=tk.LEFT)

        self.refresh_displays_button = self._build_button(display_header, self._refresh_display_list)
        self.refresh_displays_button.pack(side=tk.RIGHT)

        display_scroll = tk.Frame(self.display_section, bg=self.colors["bg"])
        display_scroll.pack(fill=tk.BOTH, expand=True, padx=12, pady=(6, 12))

        self.display_list_text = tk.Text(
            display_scroll,
            height=6,
            wrap=tk.WORD,
            bg=self.colors["entry"],
            fg=self.colors["fg"],
            relief=tk.SOLID,
            highlightthickness=1,
            highlightbackground=self.colors["border"],
        )
        scroll = tk.Scrollbar(display_scroll, command=self.display_list_text.yview)
        self.display_list_text.config(state=tk.DISABLED, yscrollcommand=scroll.set)
        self.display_list_text.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scroll.pack(side=tk.RIGHT, fill=tk.Y)

        self.status_label = tk.Label(self.root, bg=self.colors["bg"], fg=self.colors["fg"], anchor="w")
        self.status_label.pack(fill=tk.X, padx=16, pady=(0, 12))

        self.exclude_check_var.set(self.config.get_bool("OVERLAY_EXCLUDE_FROM_CAPTURE", True))
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    def _build_button(self, parent, command):
        return tk.Button(
            parent,
            command=command,
            bg=self.colors["button"],
            fg=self.colors["fg"],
            activebackground=self.colors["button_active"],
            relief=tk.RAISED,
            bd=1,
        )

    def _update_texts(self):
        self.root.title(self.i18n.tr("app.title"))
        self.title_label.config(text=self.i18n.tr("app.title"))
        self.actions_section.config(text=self.i18n.tr("section.actions"))
        self.display_section.config(text=self.i18n.tr("section.display"))
        self.select_button.config(text=self.i18n.tr("button.select_region"))
        self.solve_button.config(text=self.i18n.tr("button.capture_solve"))
        self.toggle_overlay_button.config(text=self.i18n.tr("button.toggle_overlay"))
        self.overlay_display_label.config(text=self.i18n.tr("label.overlay_display_id"))
        self.periscope_display_label.config(text=self.i18n.tr("label.periscope_display_id"))
        self.display_list_label.config(text=self.i18n.tr("label.display_list"))
        self.overlay_exclude_check.config(text=self.i18n.tr("label.exclude_capture"))
        self.apply_display_button.config(text=self.i18n.tr("button.apply_display"))
        self.refresh_displays_button.config(text=self.i18n.tr("button.refresh_displays"))
        self.language_label.config(text=self.i18n.tr("label.language"))
        self._update_periscope_label()
        self._apply_status("status.region_not_set")

    def _update_periscope_label(self):
        key = "button.stop_periscope" if self.periscope_window else "button.start_periscope"
        self.periscope_toggle_button.config(text=self.i18n.tr(key))

    def _apply_status(self, key, *args):
        self.status_label.config(text=self.i18n.tr(key, *args))

    def _on_language_change(self, _value=None):
        locale = self.language_var.get()
        self.i18n.set_locale(locale)
        self._update_texts()
        self._refresh_display_list()

    def _refresh_display_list(self):
        monitors = list_monitors()
        if not monitors:
            text = self.i18n.tr("label.detected_displays", 1) + "\n[0] default"
        else:
            lines = [self.i18n.tr("label.detected_displays", len(monitors))]
            for idx, mon in enumerate(monitors):
                left, top, right, bottom = mon["bounds"]
                width = right - left
                height = bottom - top
                lines.append(f"[{idx}] {mon['id']} bounds={left},{top} {width}x{height}")
            text = "\n".join(lines)

        self.display_list_text.config(state=tk.NORMAL)
        self.display_list_text.delete("1.0", tk.END)
        self.display_list_text.insert("1.0", text)
        self.display_list_text.config(state=tk.DISABLED)

    def _on_close(self):
        if self.hotkey:
            self.hotkey.stop()
        if self.periscope_window:
            self.periscope_window.stop()
        self.root.destroy()

    def select_region(self):
        self._apply_status("status.selecting")
        window = SelectionWindow(self.root)
        selection = window.show()
        if selection and selection[2] > 0 and selection[3] > 0:
            self.region = selection
            self.overlay.show(self.region)
            self.overlay.set_answer(self.i18n.tr("overlay.region_selected"))
            self._apply_status("status.region", selection)
        else:
            self._apply_status("status.region_not_set")

    def capture_and_solve(self):
        if self.is_processing:
            self._apply_status("status.analyzing")
            return
        if not self.region:
            self._apply_status("status.region_not_set")
            return

        self.is_processing = True
        self._apply_status("status.analyzing")
        self.solve_button.config(state=tk.DISABLED)
        self.select_button.config(state=tk.DISABLED)
        self.overlay.set_answer(self.i18n.tr("overlay.processing"))

        def worker():
            try:
                x, y, w, h = self.region
                image = ImageGrab.grab(bbox=(x, y, x + w, y + h))
                full_answer = []

                def on_chunk(chunk):
                    full_answer.append(chunk)
                    text = "".join(full_answer)
                    self.root.after(0, lambda: self.overlay.set_answer(text))

                self.ocr.recognize_stream(image, on_chunk)
                self.root.after(0, lambda: self._apply_status("status.done"))
            except Exception as exc:
                error_text = self.i18n.tr("overlay.error", str(exc))
                self.root.after(0, lambda: self.overlay.set_answer(error_text))
                self.root.after(0, lambda: self._apply_status("status.error"))
            finally:
                def reset():
                    self.solve_button.config(state=tk.NORMAL)
                    self.select_button.config(state=tk.NORMAL)
                    self.is_processing = False
                self.root.after(0, reset)

        threading.Thread(target=worker, name="solve", daemon=True).start()

    def toggle_overlay(self):
        if self.overlay.is_visible():
            self.overlay.hide()
        elif self.region:
            self.overlay.show(self.region)

    def apply_display_settings(self):
        overlay_id = self.overlay_display_var.get().strip()
        self.overlay.set_target_display_id(overlay_id)
        self.overlay.set_exclude_from_capture(self.exclude_check_var.get())
        if self.overlay.is_visible():
            self.overlay.show(self.region)

    def toggle_periscope(self):
        if self.periscope_window:
            self.periscope_window.stop()
            self.periscope_window = None
            self._apply_status("status.periscope_stopped")
        else:
            display_id = self.periscope_display_var.get().strip()
            if not display_id:
                self._apply_status("status.periscope_missing_id")
                return
            try:
                refresh_ms = self.config.get_int("PERISCOPE_REFRESH_MS", 120)
                width = self.config.get_int("PERISCOPE_WINDOW_WIDTH", 640)
                height = self.config.get_int("PERISCOPE_WINDOW_HEIGHT", 360)
                self._start_periscope(display_id, refresh_ms, width, height)
                self._apply_status("status.periscope_started")
            except Exception as exc:
                self.periscope_window = None
                self._apply_status("status.periscope_failed", str(exc))
        self._update_periscope_label()

    def _start_periscope(self, display_id, refresh_ms, width, height):
        self.periscope_window = PeriscopeWindow(self.root, display_id, refresh_ms, width, height)
        self.periscope_window.start()
        self._update_periscope_label()


def main():
    root = tk.Tk()
    app = DesktopApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
