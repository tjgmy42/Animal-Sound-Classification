# Animal Sound Classification API

API untuk mengklasifikasikan suara hewan menggunakan model YAMNet dan TensorFlow.

1. Buat virtual environment:
```bash
python -m venv animal_sound_env
```

2. Aktifkan virtual environment:
- Windows:
```bash
animal_sound_env\Scripts\activate
```
- Mac:
```bash
source animal_sound_env/bin/activate
```

4. Install dependencies:
```bash
pip install -r Requirements.txt
```

## Menjalankan Aplikasi
```bash
python main.py
```

## Menggunakan API

Setelah aplikasi berjalan, API akan tersedia di `http://localhost:8000/docs`

### Endpoints

1. **Klasifikasi Suara**
   - Endpoint: `POST /classify`
   - Method: POST
   - Body: Form-data dengan file audio
   - Response: JSON dengan hasil klasifikasi

2. **Informasi Hewan**
   - Endpoint: `GET /animals`
   - Method: GET
   - Response: JSON dengan daftar hewan yang didukung

3. **Health Check**
   - Endpoint: `GET /health`
   - Method: GET
   - Response: Status API
