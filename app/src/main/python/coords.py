"""
#-------------------------------------------------------------------------
import random

def get_coords(width, height):
    # Simula coordinate casuali (puoi sostituire con ML o eye tracking)
    x = random.randint(0, 800)
    y = random.randint(0, 1600)
    return x, y
#-------------------------------------------------------------------------
"""
"""
#-------------------------------------------------------------------------
# coords.py
import numpy as np
import cv2
from eyeGestures import EyeGestures_v2

# inizializza il motore una sola volta
gestures = EyeGestures_v2()
calibrate = True

def get_coords(frame_bytes: bytes, screen_width: int, screen_height: int):

    #frame_bytes: JPEG-encoded image
    #screen_width/height: risoluzione display Android
    #ritorna: [x, y] o [-1, -1] se non c'è evento

    global calibrate

    # decodifica JPEG -> BGR
    nparr = np.frombuffer(frame_bytes, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    # chiama EyeGestures
    event, _ = gestures.step(
        frame,
        calibrate,
        screen_width,
        screen_height,
        context="my_context"
    )

    # una volta calibrato, tienilo spento
    if calibrate and event:
        calibrate = False

    if event:
        # restituisci le coordinate
        return [event.point[0], event.point[1]]
    else:
        # default
        return [-1, -1]
#-------------------------------------------------------------------------
"""
"""
#-------------------------------------------------------------------------
import numpy as np
import cv2

# Carica i classificatori Haar per volto e occhi (pre-addestrati in OpenCV)
face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
eye_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_eye.xml")

def get_coords(frame_bytes: bytes, screen_width: int, screen_height: int):

    # Decodifica JPEG -> immagine BGR
    nparr = np.frombuffer(frame_bytes, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=3)

    if len(faces) == 0:
        return [-1, -1]

    (x, y, w, h) = faces[0]  # usa il primo volto rilevato
    roi_gray = gray[y:y+h, x:x+w]
    roi_color = frame[y:y+h, x:x+w]

    eyes = eye_cascade.detectMultiScale(roi_gray, scaleFactor=1.1, minNeighbors=3)


    if len(eyes) == 0:
        return [-1, -1]

    # Semplice centro degli occhi
    eye_centers = []
    for (ex, ey, ew, eh) in eyes:
        center_x = x + ex + ew // 2
        center_y = y + ey + eh // 2
        eye_centers.append((center_x, center_y))

    # Media delle posizioni degli occhi
    avg_eye_x = sum([pt[0] for pt in eye_centers]) // len(eye_centers)
    avg_eye_y = sum([pt[1] for pt in eye_centers]) // len(eye_centers)

    # Normalizza rispetto alla dimensione frame
    frame_h, frame_w = gray.shape
    norm_x = avg_eye_x / frame_w
    norm_y = avg_eye_y / frame_h

    # Mappa alle coordinate dello schermo
    screen_x = int(norm_x * screen_width)
    screen_y = int(norm_y * screen_height)

    return [screen_x, screen_y]

#-------------------------------------------------------------------------
"""

"""
#-------------------------------------------------------------------------

import numpy as np
import cv2
import tensorflow as tf

# Inizializzare la sessione di TensorFlow per usare il modello
tf.compat.v1.disable_eager_execution()  # Disabilita l'esecuzione eager
sess = tf.compat.v1.Session()

# Carica il grafico del modello e ripristina i pesi
saver = tf.compat.v1.train.import_meta_graph('model-23.meta')  # Carica la struttura del modello
saver.restore(sess, 'model-23')  # Ripristina i pesi

# Seleziona l'operazione di output dal grafico (questa dovrebbe essere la variabile che contiene la previsione)
gaze_direction_op = sess.graph.get_tensor_by_name('pos:0')  # Modifica 'output:0' con il nome corretto

# Carica i classificatori Haar per volto e occhi (pre-addestrati in OpenCV)
face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
eye_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_eye.xml")

# Funzione per predire la direzione dello sguardo
def predict_gaze_direction(eye_image):
    # Pre-processa l'immagine dell'occhio
    eye_image = cv2.resize(eye_image, (224, 224))  # ridimensiona l'immagine
    eye_image = np.expand_dims(eye_image, axis=0)  # aggiungi dimensione batch
    eye_image = eye_image / 255.0  # normalizza

    # Usa il modello per predire la direzione dello sguardo
    gaze_direction = sess.run(gaze_direction_op, feed_dict={'eye_right': eye_image})

    return gaze_direction

def get_coords(frame_bytes: bytes, screen_width: int, screen_height: int):
    # Decodifica JPEG -> immagine BGR
    nparr = np.frombuffer(frame_bytes, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if frame is None:
        return [-1, -1]

    # Converte l'immagine in scala di grigi
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    # Rileva i volti
    faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=3)

    if len(faces) == 0:
        return [-1, -1]

    # Seleziona il primo volto rilevato
    (x, y, w, h) = faces[0]
    roi_gray = gray[y:y+h, x:x+w]
    roi_color = frame[y:y+h, x:x+w]

    # Rileva gli occhi
    eyes = eye_cascade.detectMultiScale(roi_gray, scaleFactor=1.1, minNeighbors=3)

    if len(eyes) == 0:
        return [-1, -1]

    # Calcola i centri degli occhi
    eye_centers = []
    for (ex, ey, ew, eh) in eyes:
        # Centro dell'occhio
        center_x = x + ex + ew // 2
        center_y = y + ey + eh // 2
        eye_centers.append((center_x, center_y))

    if len(eye_centers) == 0:
        return [-1, -1]

    # Per il primo occhio (o entrambi se ne rileviamo più di uno)
    eye_x, eye_y = eye_centers[0]

    # Estrai l'immagine dell'occhio per la previsione
    eye_image = frame[ey:ey+eh, ex:ex+ew]  # Estrai l'area dell'occhio dalla ROI
    gaze_direction = predict_gaze_direction(eye_image)

    # Calcolare la direzione dello sguardo come coordinate normalizzate
    gaze_x, gaze_y = gaze_direction[0]  # Assumendo che il modello ritorni [x, y] come una lista

    # Mappa alle coordinate dello schermo
    screen_x = int(gaze_x * screen_width)
    screen_y = int(gaze_y * screen_height)

    return [screen_x, screen_y]
#-------------------------------------------------------------------------

"""

import numpy as np
import cv2

# Carica i classificatori Haar per volto e occhi (pre-addestrati in OpenCV)
face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
eye_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_eye.xml")

def get_coords(frame_bytes: bytes, screen_width: int, screen_height: int):
    # Decodifica JPEG -> immagine BGR
    nparr = np.frombuffer(frame_bytes, np.uint8)
    frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if frame is None:
        return [-1, -1]

    # Converte l'immagine in scala di grigi
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    # Rileva i volti
    faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=3)

    if len(faces) == 0:
        return [-1, -1]

    # Seleziona il primo volto rilevato
    (x, y, w, h) = faces[0]
    roi_gray = gray[y:y+h, x:x+w]
    roi_color = frame[y:y+h, x:x+w]

    # Rileva gli occhi
    eyes = eye_cascade.detectMultiScale(roi_gray, scaleFactor=1.1, minNeighbors=3)

    if len(eyes) == 0:
        return [-1, -1]

    # Calcola i centri degli occhi
    eye_centers = []
    for (ex, ey, ew, eh) in eyes:
        # Centro dell'occhio
        center_x = x + ex + ew // 2
        center_y = y + ey + eh // 2
        eye_centers.append((center_x, center_y))

    if len(eye_centers) == 0:
        return [-1, -1]

    # Calcola il centro medio degli occhi
    avg_eye_x = sum([pt[0] for pt in eye_centers]) // len(eye_centers)
    avg_eye_y = sum([pt[1] for pt in eye_centers]) // len(eye_centers)

    # Normalizza rispetto alla dimensione del frame
    frame_h, frame_w = gray.shape
    norm_x = avg_eye_x / frame_w
    norm_y = avg_eye_y / frame_h

    # Mappa alle coordinate dello schermo
    screen_x = int(norm_x * screen_width)
    screen_y = int(norm_y * screen_height)

    return [screen_x, screen_y]