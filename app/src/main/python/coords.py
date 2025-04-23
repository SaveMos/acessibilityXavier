"""
import random

def get_coords(width, height):
    # Simula coordinate casuali (puoi sostituire con ML o eye tracking)
    x = random.randint(0, 800)
    y = random.randint(0, 1600)
    return x, y
"""
"""
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
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    faces = face_cascade.detectMultiScale(gray, 1.3, 5)

    if len(faces) == 0:
        return [-1, -1]

    (x, y, w, h) = faces[0]  # usa il primo volto rilevato
    roi_gray = gray[y:y+h, x:x+w]
    roi_color = frame[y:y+h, x:x+w]

    eyes = eye_cascade.detectMultiScale(roi_gray)

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

