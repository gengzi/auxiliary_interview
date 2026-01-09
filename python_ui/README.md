Python UI port

Requirements:
- Python 3.10+
- Windows (click-through and capture exclusion are best-effort)
- Install deps: pip install -r requirements.txt

Run:
- python app.py

Notes:
- The UI reads config from app.properties, with env var overrides.
- Global hotkey Ctrl+P uses Win32 RegisterHotKey when available.
