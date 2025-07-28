# Animal Sound Identification Feature for ZooSite Application

## Project Information

- **Author**: Anggara Saputra  
- **Student ID**: 5025211241  
- **Institution**: Institut Teknologi Sepuluh Nopember (ITS)

## System Architecture

### Application Development Flowchart
![ZooSite Feature Development Flowchart](https://github.com/user-attachments/assets/97474355-58c8-4ca6-b21f-cec53aa78923)

### Android Application Flowchart
![Android Application System Flowchart](https://github.com/user-attachments/assets/abb34dbe-e91e-4082-a178-763c72717697)

## Dataset

**Dataset Access**: [Google Drive Link](https://drive.google.com/drive/folders/1w48fiWkJgzQO_4CYn8HJGTQcyFdNC3OL?usp=sharing)

## Getting Started

### Backend API Setup

The backend API uses YAMNet model and TensorFlow for animal sound classification.

#### Installation Steps

1. **Navigate to the backend directory**
   ```bash
   cd Backend-API
   ```

2. **Create a virtual environment**
   ```bash
   python -m venv animal_sound_env
   ```

3. **Activate the virtual environment**
   
   **Windows:**
   ```bash
   animal_sound_env\Scripts\activate
   ```
   
   **macOS/Linux:**
   ```bash
   source animal_sound_env/bin/activate
   ```

4. **Install required dependencies**
   ```bash
   pip install -r Requirements.txt
   ```

5. **Start the application**
   ```bash
   python main.py
   ```

The API will be available at `http://localhost:8000`

#### API Documentation

Once the application is running, you can access the interactive API documentation at:
`http://localhost:8000/docs`

#### Available Endpoints

| Endpoint | Method | Description | Parameters |
|----------|--------|-------------|------------|
| `/classify` | POST | Classify animal sounds from audio files | Form-data: audio file |
| `/animals` | GET | Retrieve animal information database | None |
| `/health` | GET | Check API health status | None |

**Response Format**: JSON containing classification results and animal information

### Android Application Setup

#### Installation Steps

1. **Open Android Studio**

2. **Import the project**
   - Select "Open an existing Android Studio project"
   - Navigate to and select the `TugasAkhirZooSite` folder

3. **Build and run the application**
   - Click the Run ▶ button in Android Studio
   - Select your target device or emulator

## How to Use the Application

### Step 1: Access Audio Classification Feature
Select the "Audio Classify" menu from the main application interface.

![Audio Classify Menu](https://github.com/user-attachments/assets/9a3e9f11-74be-443b-b4e6-cd95288667e8)

### Step 2: Choose Audio Input Method
You can either:
- Select an existing audio file from your device's file manager
- Record audio directly using the built-in recorder

![Audio Input Selection](https://github.com/user-attachments/assets/98e161a1-60be-4b8f-818e-1fa1af2b0b01)

### Step 3: Perform Classification
Click the checkmark button to start the animal sound classification process.

### Step 4: View Results
The application will display:
- Photo of the identified animal
- Animal name
- Unique facts about the animal
- Detailed description

![Classification Results](https://github.com/user-attachments/assets/7c0bc8e2-db85-407a-b842-9a3468eba43d)
