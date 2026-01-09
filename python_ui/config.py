import os


class AppConfig:
    def __init__(self, path):
        self._props = {}
        self._path = path
        self._load()

    def _load(self):
        if not os.path.exists(self._path):
            return
        with open(self._path, "r", encoding="utf-8") as handle:
            for raw in handle:
                line = raw.strip()
                if not line or line.startswith("#"):
                    continue
                if "=" not in line:
                    continue
                key, value = line.split("=", 1)
                self._props[key.strip()] = value.strip()

    def get(self, key, default=None):
        env = os.environ.get(key)
        if env is not None and env.strip() != "":
            return env
        return self._props.get(key, default)

    def get_int(self, key, default=0):
        value = self.get(key, str(default))
        try:
            return int(str(value).strip())
        except (TypeError, ValueError):
            return default

    def get_bool(self, key, default=False):
        value = str(self.get(key, str(default))).strip().lower()
        return value in ("1", "true", "yes", "y", "on")
