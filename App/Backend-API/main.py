import os
# Enhanced environment variables to fix SVML errors
os.environ['TF_DISABLE_MKL'] = '1'
os.environ['TF_DISABLE_SEGMENT_REDUCTION_OP_DETERMINISM_EXCEPTIONS'] = 'true'
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'
# Additional variables to fix SVML issues
os.environ['NUMBA_DISABLE_INTEL_SVML'] = '1'
os.environ['NUMBA_NUM_THREADS'] = '1'
os.environ['OMP_NUM_THREADS'] = '1'

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
import numpy as np
import tempfile
from typing import Dict, Any
import logging

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Get port from environment variable or use default
PORT = int(os.getenv("PORT", 8000))

# Import librosa after setting environment variables
try:
    import librosa
    logger.info(f"Librosa version: {librosa.__version__}")
except ImportError as e:
    logger.error(f"Failed to import librosa: {e}")
    raise e

# Try to import TensorFlow with different approaches
try:
    import tensorflow as tf
    from tensorflow import keras
    logger.info(f"TensorFlow version: {tf.__version__}")
except ImportError as e:
    logger.error(f"Failed to import TensorFlow: {e}")
    raise e

# Initialize FastAPI app
app = FastAPI(
    title="Animal Sound Classification API",
    description="API untuk mengklasifikasikan suara hewan menggunakan model YAMNet",
    version="1.0.0"
)

# Animal data dictionary
ANIMAL_DATA = {
  "beruang": {
    "animal": "Beruang",
    "description": "Beruang berkomunikasi melalui suara geraman, lenguhan, dan auman untuk mengintimidasi musuh atau menarik perhatian anak-anak mereka. Beruang juga dapat membuat suara mendengus atau menggeram ketika merasa terancam.",
    "fact": "Beruang memiliki indera penciuman yang sangat tajam dan dapat mencium bau makanan dari jarak yang sangat jauh, bahkan lebih dari beberapa kilometer. Mereka adalah hewan yang soliter dan hanya berkumpul untuk berkembang biak atau saat ibu sedang merawat anak-anaknya."
  },
  "cendrawasih": {
    "animal": "Cendrawasih",
    "description": "Burung Cendrawasih terkenal dengan bulu-bulunya yang indah dan suara panggilan yang unik, terutama saat musim kawin. Mereka menggunakan berbagai panggilan bernada tinggi untuk menarik pasangan dan menandai wilayah.",
    "fact": "Burung Cendrawasih memiliki tarian kawin yang rumit untuk menarik perhatian betina, dengan banyak spesies menunjukkan bulu warna-warni dan gerakan menakjubkan. Mereka merupakan simbol keindahan alam Papua dan dilindungi dari kepunahan."
  },
  "domba": {
    "animal": "Domba",
    "description": "Domba dikenal karena suara khas mereka yang disebut 'mbeeek'. Mereka biasanya hidup dalam kawanan dan sering digunakan manusia untuk wol, daging, dan susu.",
    "fact": "Domba memiliki kemampuan mengenali wajah domba lain dan manusia yang mereka temui. Mereka juga menunjukkan tanda-tanda keterikatan emosional dengan kawanan mereka."
  },
  "elang": {
    "animal": "Elang Jawa",
    "description": "Elang Jawa adalah burung pemangsa khas Indonesia yang memiliki ciri khas jambul panjang di kepalanya. Mereka dikenal dengan vokalisasi mereka yang kuat, yang digunakan untuk menandai wilayah dan berkomunikasi dengan pasangan serta keturunannya.",
    "fact": "Elang Jawa adalah simbol nasional Indonesia, dijuluki 'Garuda', dan merupakan spesies langka yang terancam punah. Mereka sangat setia pada wilayahnya dan dapat membangun sarang yang sama selama bertahun-tahun."
  },
  "gajah": {
    "animal": "Gajah",
    "description": "Gajah adalah mamalia darat terbesar yang menggunakan berbagai vokalisasi untuk berkomunikasi, termasuk suara terompet saat mereka merasa terancam atau bersemangat. Selain itu, mereka menghasilkan gemuruh frekuensi rendah yang sulit terdengar oleh manusia namun dapat merambat jarak jauh.",
    "fact": "Gajah memiliki memori luar biasa yang memungkinkan mereka mengingat jalur migrasi dan lokasi sumber air selama bertahun-tahun. Mereka juga dikenal sebagai hewan yang empatik dan sering menunjukkan perilaku berkabung ketika salah satu anggota mereka mati."
  },
  "kasuari": {
    "animal": "Kasuari",
    "description": "Burung Kasuari adalah burung besar yang memiliki suara dengungan dalam yang dapat merambat jauh melalui hutan. Suara mereka digunakan untuk menandai wilayah dan selama musim kawin.",
    "fact": "Kasuari adalah salah satu burung paling berbahaya di dunia dengan cakar tajam di kaki mereka. Mereka hidup di hutan tropis Papua dan memainkan peran penting dalam penyebaran biji tanaman."
  },
  "katak": {
    "animal": "Katak",
    "description": "Katak menghasilkan suara ribut, sering disebut 'berkrokok', terutama selama musim kawin untuk menarik pasangan. Suara berkrokok mereka unik pada setiap spesies dan dapat terdengar saat malam.",
    "fact": "Katak adalah indikator ekologi yang baik karena sensitivitas mereka terhadap perubahan lingkungan. Kehadiran atau ketiadaan katak di suatu daerah dapat menunjukkan kesehatan ekosistem lokal."
  },
  "keledai": {
    "animal": "Keledai",
    "description": "Keledai dikenal dengan suaranya yang khas, sering disebut 'ngeheeek', yang dapat terdengar dari jarak jauh. Suara ini digunakan untuk mengumumkan kehadiran mereka atau untuk berkomunikasi dengan kawanan.",
    "fact": "Keledai memiliki daya ingat luar biasa dan bisa mengingat lokasi dan rute selama bertahun-tahun. Mereka adalah hewan yang sangat tangguh dan bisa bertahan hidup di lingkungan yang keras dengan sedikit air dan makanan."
  },
  "kuda": {
    "animal": "Kuda",
    "description": "Kuda menggunakan meringkik sebagai ekspresi emosi, termasuk kegembiraan, kecemasan, atau rasa ingin tahu. Mereka juga dapat mendengus dan meringis, setiap suara memiliki arti yang berbeda dalam komunikasi sosial mereka.",
    "fact": "Kuda memiliki kemampuan mengenali suara manusia yang akrab, bahkan dapat merespons panggilan pemiliknya. Mereka adalah hewan sosial yang memiliki struktur sosial kompleks, dan lebih sering ditemukan dalam kelompok yang dipimpin oleh kuda betina."
  },
  "lumba-lumba": {
    "animal": "Lumba-lumba",
    "description": "Lumba-lumba menggunakan klik, peluit, dan suara berdetak untuk berkomunikasi dan berburu. Suara ini membantu mereka dalam echolocation untuk mendeteksi mangsa dan objek di dalam air.",
    "fact": "Lumba-lumba adalah hewan yang cerdas dan sering bekerja sama dalam kelompok besar untuk berburu. Mereka bahkan menunjukkan rasa kepedulian terhadap satu sama lain, dan diketahui membantu sesama lumba-lumba yang terluka atau sakit."
  },
  "monyet": {
    "animal": "Monyet",
    "description": "Monyet berkomunikasi menggunakan berbagai vokalisasi, termasuk jeritan, teriakan, dan celotehan untuk berinteraksi dalam kelompok sosialnya. Mereka memiliki vokalisasi yang berbeda untuk memberi tanda bahaya, peringatan, atau bermain.",
    "fact": "Monyet memiliki kemampuan untuk menggunakan alat sederhana dan menunjukkan perilaku sosial yang rumit. Beberapa spesies bahkan dapat meniru ekspresi wajah dan gerakan satu sama lain untuk menunjukkan emosi. Kehidupan sosial yang erat membuat mereka sangat bergantung pada komunikasi suara."
  },
  "sapi": {
    "animal": "Sapi",
    "description": "Sapi menghasilkan suara melenguh, yang sering digunakan untuk berkomunikasi dengan anak sapi atau dengan kawanan lainnya. Mereka juga menggunakan suara untuk menunjukkan rasa lapar atau ketidaknyamanan.",
    "fact": "Sapi adalah hewan yang sosial dan memiliki kemampuan mengenali satu sama lain dalam kawanan. Mereka dapat mengingat teman dan memiliki hubungan sosial yang kompleks dalam kelompok mereka."
  },
  "serigala": {
    "animal": "Serigala",
    "description": "Serigala terkenal dengan lolongan mereka yang sering digunakan untuk berkomunikasi dengan anggota kelompok atau memperingatkan serigala lain di wilayah mereka.",
    "fact": "Serigala hidup dalam kelompok terorganisir yang disebut kawanan. Mereka memiliki struktur sosial hierarkis dan biasanya bekerja sama untuk berburu mangsa besar."
  },
  "singa": {
    "animal": "Singa",
    "description": "Singa dikenal sebagai 'raja hutan' dan sering mengaum untuk menunjukkan kehadirannya dan menandai wilayah mereka. Auman singa memiliki suara yang dalam dan bergema, sering digunakan untuk mengkoordinasikan anggota kelompok dan mengusir predator.",
    "fact": "Auman singa bisa terdengar hingga jarak 8 kilometer di padang rumput terbuka, membantu mereka menjaga kohesi dalam kelompok besar yang disebut kawanan. Singa adalah satu-satunya spesies kucing besar yang hidup dalam kelompok."
  },
  "sulawesi_scops_owl": {
    "animal": "Sulawesi Scops Owl",
    "description": "Burung hantu kecil endemik Sulawesi ini dikenal dengan suara panggilannya yang khas berupa siulan lembut dan pendek, yang biasanya terdengar saat malam hari. Mereka aktif di malam hari dan menggunakan vokalisasi untuk menandai wilayah serta berkomunikasi dengan pasangan.",
    "fact": "Sulawesi Scops Owl hidup di hutan-hutan tropis dan lebih sering terdengar daripada terlihat karena warna bulunya yang menyatu dengan lingkungan. Mereka memiliki pendengaran yang sangat tajam untuk berburu serangga dan hewan kecil di malam hari."
  },
  "unknown": {
    "animal": "Unknown Sound",
    "description": "Suara Binatang tidak diketahui",
    "fact": "Suara Binatang tidak diketahui"
  }
}


# Global variables to store models
yamnet_model = None
classifier_model = None

def load_yamnet_model():
    """Load YAMNet model for feature extraction"""
    global yamnet_model
    try:
        # Load YAMNet from TensorFlow Hub
        import tensorflow_hub as hub
        yamnet_model = hub.load('https://tfhub.dev/google/yamnet/1')
        logger.info("YAMNet model loaded successfully from TensorFlow Hub")
        return True
    except Exception as e:
        logger.warning(f"Failed to load YAMNet from TensorFlow Hub: {e}")
        
        # Alternative: try to load a local YAMNet model if available
        try:
            yamnet_model = tf.saved_model.load('yamnet_model')  # if you have local yamnet
            logger.info("YAMNet model loaded from local directory")
            return True
        except Exception as e2:
            logger.error(f"Failed to load local YAMNet model: {e2}")
            return False

def extract_yamnet_features(audio_array: np.ndarray) -> np.ndarray:
    """
    Extract YAMNet features from audio
    
    Args:
        audio_array: Audio waveform array
    
    Returns:
        YAMNet embeddings (1024-dimensional features)
    """
    try:
        if yamnet_model is None:
            raise Exception("YAMNet model not loaded")
        
        # YAMNet expects float32 audio at 16kHz
        audio_tensor = tf.convert_to_tensor(audio_array, dtype=tf.float32)
        
        # Get YAMNet embeddings
        _, embeddings, _ = yamnet_model(audio_tensor)
        
        # Average the embeddings across time to get a single 1024-d vector
        mean_embeddings = tf.reduce_mean(embeddings, axis=0)
        
        return mean_embeddings.numpy()
    except Exception as e:
        logger.error(f"Error extracting YAMNet features: {str(e)}")
        raise e

def extract_manual_features(audio_array: np.ndarray, sr: int = 16000) -> np.ndarray:
    try:
        features = []
        
        # Extract MFCCs
        mfccs = librosa.feature.mfcc(y=audio_array, sr=sr, n_mfcc=13)
        features.extend(np.mean(mfccs, axis=1))
        features.extend(np.std(mfccs, axis=1))
        
        # Extract spectral features
        spectral_centroids = librosa.feature.spectral_centroid(y=audio_array, sr=sr)[0]
        features.append(np.mean(spectral_centroids))
        features.append(np.std(spectral_centroids))
        
        spectral_rolloff = librosa.feature.spectral_rolloff(y=audio_array, sr=sr)[0]
        features.append(np.mean(spectral_rolloff))
        features.append(np.std(spectral_rolloff))
        
        spectral_bandwidth = librosa.feature.spectral_bandwidth(y=audio_array, sr=sr)[0]
        features.append(np.mean(spectral_bandwidth))
        features.append(np.std(spectral_bandwidth))
        
        # Zero crossing rate
        zcr = librosa.feature.zero_crossing_rate(audio_array)[0]
        features.append(np.mean(zcr))
        features.append(np.std(zcr))
        
        # Chroma features
        chroma = librosa.feature.chroma_stft(y=audio_array, sr=sr)
        features.extend(np.mean(chroma, axis=1))
        features.extend(np.std(chroma, axis=1))
        
        # Convert to numpy array
        feature_vector = np.array(features)
        
        # Pad or truncate to 1024 dimensions
        if len(feature_vector) < 1024:
            feature_vector = np.pad(feature_vector, (0, 1024 - len(feature_vector)), mode='constant')
        elif len(feature_vector) > 1024:
            feature_vector = feature_vector[:1024]
        
        return feature_vector
    except Exception as e:
        logger.error(f"Error extracting manual features: {str(e)}")
        raise e

def load_classifier_model():
    """Load the animal classification model"""
    global classifier_model
    model_path = 'yamnet_final_model.h5'
    
    # Check if model file exists
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"Model file '{model_path}' not found. Please ensure the file exists in the current directory.")
    
    logger.info(f"Model file exists at: {model_path}")
    logger.info(f"Model file size: {os.path.getsize(model_path) / (1024*1024):.2f} MB")
    
    try:
        # Method 1: Try keras.models.load_model with custom_objects
        logger.info("Attempting to load classifier model using keras.models.load_model with custom_objects...")
        classifier_model = keras.models.load_model(model_path, custom_objects={'InputLayer': tf.keras.layers.InputLayer})
        logger.info("Classifier model loaded successfully using keras.models.load_model")
        logger.info(f"Model summary: {classifier_model.summary()}")
        return
    except Exception as e1:
        logger.warning(f"Failed to load using keras.models.load_model: {str(e1)}")
        
        try:
            # Method 2: Try tf.keras.models.load_model with custom_objects
            logger.info("Attempting to load classifier model using tf.keras.models.load_model with custom_objects...")
            classifier_model = tf.keras.models.load_model(model_path, custom_objects={'InputLayer': tf.keras.layers.InputLayer})
            logger.info("Classifier model loaded successfully using tf.keras.models.load_model")
            logger.info(f"Model summary: {classifier_model.summary()}")
            return
        except Exception as e2:
            logger.warning(f"Failed to load using tf.keras.models.load_model: {str(e2)}")
            
            try:
                # Method 3: Try loading with compile=False and custom_objects
                logger.info("Attempting to load classifier model with compile=False and custom_objects...")
                classifier_model = tf.keras.models.load_model(model_path, compile=False, custom_objects={'InputLayer': tf.keras.layers.InputLayer})
                logger.info("Classifier model loaded successfully with compile=False")
                logger.info(f"Model summary: {classifier_model.summary()}")
                return
            except Exception as e3:
                logger.error(f"All classifier model loading methods failed:")
                logger.error(f"Method 1 error: {str(e1)}")
                logger.error(f"Method 2 error: {str(e2)}")
                logger.error(f"Method 3 error: {str(e3)}")
                logger.error(f"TensorFlow version: {tf.__version__}")
                raise Exception(f"Failed to load classifier model after trying all methods. Last error: {str(e3)}")

def preprocess_audio(audio_path: str, target_sr: int = 16000) -> np.ndarray:
    """
    Preprocess audio file for feature extraction
    
    Args:
        audio_path: Path to the audio file
        target_sr: Target sample rate (YAMNet uses 16kHz)
    
    Returns:
        Preprocessed audio array
    """
    try:
        # Load audio file with safer librosa settings
        audio, sr = librosa.load(audio_path, sr=target_sr, dtype=np.float32)
        
        # Normalize audio
        if np.max(np.abs(audio)) > 0:
            audio = audio / np.max(np.abs(audio))
        
        # Ensure minimum length (YAMNet typically expects at least 1 second)
        min_length = target_sr  # 1 second
        if len(audio) < min_length:
            audio = np.pad(audio, (0, min_length - len(audio)), mode='constant')
        
        return audio
    except Exception as e:
        logger.error(f"Error preprocessing audio: {str(e)}")
        raise e

def predict_animal_sound(audio_array: np.ndarray) -> Dict[str, Any]:
    try:
        # Extract features
        if yamnet_model is not None:
            logger.info("Extracting YAMNet features...")
            features = extract_yamnet_features(audio_array)
        else:
            logger.info("YAMNet not available, extracting manual features...")
            features = extract_manual_features(audio_array)
        
        logger.info(f"Extracted features shape: {features.shape}")
        
        # Expand dimensions to match model input shape (batch dimension)
        features_input = np.expand_dims(features, axis=0)
        logger.info(f"Features input shape: {features_input.shape}")
        
        # Make prediction
        predictions = classifier_model.predict(features_input, verbose=0)
        
        # Get the predicted class index
        predicted_class_idx = np.argmax(predictions, axis=1)[0]
        confidence = float(np.max(predictions))
        
        # Map class index to animal key
        animal_keys = list(ANIMAL_DATA.keys())
        # Remove 'unknown' from the list since it's not a real class
        animal_keys = [key for key in animal_keys if key != 'unknown']
        
        # Check if predicted class is within valid range
        if predicted_class_idx < len(animal_keys):
            predicted_animal_key = animal_keys[predicted_class_idx]
        else:
            predicted_animal_key = "unknown"
        
        logger.info(f"Predicted animal key: {predicted_animal_key}")
        logger.info(f"Class index: {predicted_class_idx}")
        logger.info(f"Available animal keys: {animal_keys}")
        
        return {
            "predicted_class": predicted_animal_key,
            "confidence": confidence,
            "class_index": int(predicted_class_idx)
        }
    except Exception as e:
        logger.error(f"Error in prediction: {str(e)}")
        raise e

@app.on_event("startup")
async def startup_event():
    """Load models on startup"""
    try:
        # Load YAMNet model (for feature extraction)
        yamnet_loaded = load_yamnet_model()
        if not yamnet_loaded:
            logger.warning("YAMNet model failed to load, will use manual feature extraction as fallback")
        
        # Load classifier model
        load_classifier_model()
        logger.info("Startup completed successfully")
    except Exception as e:
        logger.error(f"Startup failed: {e}")
        # Don't raise here to allow API to start even if model fails to load
        # The model loading error will be caught in the classify endpoint

@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "message": "Animal Sound Classification API", 
        "status": "running",
        "tensorflow_version": tf.__version__,
        "librosa_version": librosa.__version__,
        "yamnet_loaded": yamnet_model is not None,
        "classifier_loaded": classifier_model is not None
    }

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy", 
        "yamnet_loaded": yamnet_model is not None,
        "classifier_loaded": classifier_model is not None,
        "tensorflow_version": tf.__version__,
        "librosa_version": librosa.__version__
    }

@app.post("/classify")
async def classify_animal_sound(file: UploadFile = File(...)):
    """
    Classify animal sound from uploaded audio file
    
    Args:
        file: Uploaded audio file (.wav format)
    
    Returns:
        JSON response with classification results
    """
    try:
        if classifier_model is None:
            logger.error("Classifier model not loaded")
            raise HTTPException(status_code=500, detail="Classifier model not loaded")
            
        # Save uploaded file temporarily
        with tempfile.NamedTemporaryFile(delete=False, suffix='.wav') as temp_file:
            content = await file.read()
            temp_file.write(content)
            temp_path = temp_file.name
            
        logger.info(f"Saved uploaded file to: {temp_path}")
        
        try:
            # Preprocess audio
            logger.info("Preprocessing audio...")
            audio_array = preprocess_audio(temp_path)
            logger.info(f"Audio array shape: {audio_array.shape}")
            
            # Make prediction
            logger.info("Making prediction...")
            prediction = predict_animal_sound(audio_array)
            logger.info(f"Prediction result: {prediction}")
            
            # Get animal data
            animal_key = prediction["predicted_class"]
            if animal_key in ANIMAL_DATA:
                animal_data = ANIMAL_DATA[animal_key]
            else:
                logger.warning(f"Animal key '{animal_key}' not found in ANIMAL_DATA, using 'unknown'")
                animal_data = ANIMAL_DATA["unknown"]
            
            return {
                "prediction": prediction,
                "animal_data": animal_data
            }
            
        finally:
            # Clean up temporary file
            os.unlink(temp_path)
            logger.info(f"Cleaned up temporary file: {temp_path}")
            
    except Exception as e:
        logger.error(f"Error in classify_animal_sound: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/animals")
async def get_animals():
    """Get list of all available animals"""
    return {
        "animals": [
            {
                "key": key,
                "name": data["animal"],
                "description": data["description"]
            }
            for key, data in ANIMAL_DATA.items()
            if key != "unknown"  # Don't include unknown in the list
        ]
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=PORT)