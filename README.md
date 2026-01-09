Interview Copilot POC

Modules
- backend: Spring Boot + Spring AI service
- desktop: Swing + JNA overlay + local OCR

Prereqs
- JDK 17+
- Local Tesseract OCR installed, with tessdata files

Backend
- Configure env:
  - OPENAI_API_KEY
  - OPENAI_BASE_URL (optional)
  - OPENAI_MODEL (optional)
- Run:
  - ./gradlew :backend:bootRun

Desktop
- Configure env if needed:
  - BACKEND_URL (default http://localhost:8080)
  - TESSERACT_PATH (optional, Tesseract install dir)
  - TESSDATA_PATH (optional, tessdata dir)
  - TESSERACT_LANG (default eng)
- Run:
  - ./gradlew :desktop:run

Usage
1) Start backend
2) Start desktop
3) Click "Select Region", drag over the question area
4) Click "Capture + Solve"

Notes
- Overlay is transparent and click-through on Windows
- If OCR is poor, try setting TESSDATA_PATH or language
