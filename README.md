# Animal Sound Identification Feature Development in ZooSite Application Using Convolutional Neural Network (CNN)

## Project Information

**Author**: Anggara Saputra  
**Student ID**: 5025211241  
**Institution**: Institut Teknologi Sepuluh Nopember (ITS)

---

## System Flowchart

<img width="4892" height="328" alt="System Flowchart" src="https://github.com/user-attachments/assets/abb34dbe-e91e-4082-a178-763c72717697" />

---

## Dataset

**Dataset Link**: [Available on Google Drive](link_drive)

---

## How to Use

### Option 1: Run the Full System (Developer Mode)

#### Backend API Setup

API for classifying animal sounds using YAMNet model and TensorFlow.

1. **Navigate to Backend Directory**
   ```bash
   cd Backend-API
   ```

2. **Create Virtual Environment**
   ```bash
   python -m venv animal_sound_env
   ```

3. **Activate Virtual Environment**
   - **Windows:**
     ```bash
     animal_sound_env\Scripts\activate
     ```
   - **Mac/Linux:**
     ```bash
     source animal_sound_env/bin/activate
     ```

4. **Install Dependencies**
   ```bash
   pip install -r Requirements.txt
   ```

5. **Run the Application**
   ```bash
   python main.py
   ```

#### API Usage

After the application is running, the API will be available at `http://localhost:8000/docs`

##### Available Endpoints

| Endpoint | Method | Description | Body/Parameters |
|----------|--------|-------------|-----------------|
| `/classify` | POST | Sound Classification | Form-data with audio file |
| `/animals` | GET | Animal Information | - |
| `/health` | GET | Health Check | - |

**Response Format**: JSON with classification results

#### Android Application Setup

1. **Open Android Studio**
2. **Open Project**: Select the `TugasAkhirZooSite` folder
3. **Run Application**: Click the Run ▶ button in Android Studio

---

### Option 2: Use the APK (For Testers/Users)

For users who don't want to run the backend and Android Studio:

1. **Download APK** from [Releases](link_to_releases)
2. **Install APK** on Android device
