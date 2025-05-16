import numpy as np
from scipy.signal import butter, lfilter, filtfilt, welch

from numpy import trapz
import joblib
import pandas as pd
from os.path import dirname,join

from sklearn.ensemble import GradientBoostingClassifier
from sklearn.preprocessing import StandardScaler



fs=500
modello=None
scaler=None

def model_init():
    try:
        global modello
        global scaler
        #salvati dove c'è lo script python
        model_path=join(dirname(__file__),"XavierModel.sav")
        scaler_path=join(dirname(__file__),"scaler.pkl")

        #Caricamento modello
        modello = joblib.load(model_path)
        #Caricamento scaler
        scaler = joblib.load(scaler_path)        


        return True
    except Exception as e:
        print(f"Python [init]: Exception occurred: {str(e)}")
        return False  # Torna False in caso di errore per evitare crash



def lowpass(sig, fc=10, sampling_frequency_hz=fs, filter_order=4):
    """
    Apply a low-pass filter to a given signal.

    This function uses the Butterworth filter to apply a low-pass filter to
    the input signal along the specified axis. The cutoff frequency, sampling
    frequency, and filter order can be customized.

    :param sig: The input signal to be filtered. It can be a 1D or multidimensional
        array-like structure where filtering is applied along the first axis.
    :type sig: array-like
    :param fc: The cutoff frequency of the low-pass filter in Hertz. Defaults to 10 Hz.
    :type fc: float
    :param sampling_frequency_hz: The sampling frequency of the input signal in Hertz.
        Required to normalize the cutoff frequency for the Butterworth filter.
    :type sampling_frequency_hz: float
    :param filter_order: The order of the Butterworth filter. Higher orders result in a
        steeper roll-off. Defaults to 4.
    :type filter_order: int
    :return: The filtered signal with the same shape as the input signal, where the
        specified low-pass filtering has been applied.
    :rtype: ndarray
    """
    B, A = butter(filter_order, np.array(fc) / (sampling_frequency_hz / 2), btype='low')
    return lfilter(B, A, sig, axis=0)


def bandpass(data, lowcut, highcut, sampling_frequency_hz=fs, filter_order=4):
    """
    Apply a bandpass filter to the data between lowcut and highcut frequencies.

    :param data: 1D NumPy array, the signal to be filtered
    :param lowcut: Low cutoff frequency in Hz
    :param highcut: High cutoff frequency in Hz
    :param sampling_frequency_hz: Sampling frequency in Hz
    :param filter_order: Order of the Butterworth filter
    :return: Filtered signal
    """
    nyquist = 0.5 * sampling_frequency_hz
    low = lowcut / nyquist
    high = highcut / nyquist
    b, a = butter(filter_order, [low, high], btype='band')
    return filtfilt(b, a, data)

def combine_filtered_channels(data_signal, method='sum'):
    """
    Combines EEG data channels using a specified method and filters the combined signals.

    The function takes an input signal containing time and EEG data channels, combines
    the EEG data channels using a specified method (e.g., sum, mean, or max), and then
    applies a lowpass filter to the combined data. The resulting filtered signal is
    returned along with the corresponding time information.

    :param data_signal: Input array, where the first column is time and the remaining
        columns represent EEG data from various channels.
    :type data_signal: numpy.ndarray
    :param sampling_frequency_hz: Sampling frequency of the input signal in hertz, used
        for filtering purposes, defaults to 'fs'.
    :type sampling_frequency_hz: Float
    :param fc: Cut-off frequency for the lowpass filter, defaults to 10.
    :type fc: Float
    :param method: Method to combine EEG data channels. Possible methods include
        'sum', 'mean', or 'max'. Defaults to 'sum'.
    :type method: Str.
    :return: A 2D numpy array with the first column representing time and the second
        column containing the filtered combined EEG signal.
    :rtype: numpy.ndarray
    """

    # Combine filtered channels (on columns)
    if method == 'sum':
        combined = np.sum(data_signal, axis=1)
    elif method == 'mean':
        combined = np.mean(data_signal, axis=1)
    elif method == 'max':
        combined = np.max(data_signal, axis=1)
    else:
        combined = np.sum(data_signal, axis=1)

    # 1. Apply lowpass filter
    combined = lowpass(combined)

    # 2. Apply bandpass filter [1, 50] Hz
    combined = bandpass(combined, lowcut=10, highcut=30)

    return combined

def band_power(frequencies, psd, low_freq, high_freq):
    """
    Calculates the band power of a given power spectral density (PSD) within a specified
    frequency range. The function integrates the PSD over the frequency range defined
    by `low_freq` and `high_freq` using the trapezoidal rule.

    :param frequencies: Array of frequency values corresponding to the PSD.
    :type frequencies: numpy.ndarray
    :param psd: Power spectral density values corresponding to the frequencies.
    :type psd: numpy.ndarray
    :param low_freq: Lower bound of the frequency range for calculating band power.
    :type low_freq: float
    :param high_freq: Upper bound of the frequency range for calculating band power.
    :type high_freq: float
    :return: The calculated band power as a single float value.
    :rtype: float
    """
    beta_mask = (frequencies >= low_freq) & (frequencies <= high_freq)
    return trapz(psd[beta_mask], frequencies[beta_mask])


def EEG_classifier(buffer, n_canali):
    try:
        buffer = np.frombuffer(buffer, dtype=np.float32)
        if buffer.size % n_canali != 0:
            buffer = buffer[:(buffer.size // n_canali) * n_canali]

        window = buffer.reshape(-1, n_canali)
        #   500 campioni * 6 canali
        #   ch1|ch2|ch3|ch4|ch5|ch6
        #1
        #2
        #...
        #500
        combined_signal=combine_filtered_channels(window, method='sum')
        frequencies, psd = welch(combined_signal, fs=fs, nperseg=min(1024, len(combined_signal)))

        alpha=np.real(band_power(frequencies, psd, 8, 12.9))  # Alpha band power.
        beta=np.real(band_power(frequencies, psd, 13, 29.9))  # Beta band power.
        gamma=np.real(band_power(frequencies, psd, 30, 50))  # Gamma band power.
        theta=np.real(band_power(frequencies, psd, 4, 7.9))  # Theta band power

        X = pd.DataFrame([{
            "Alpha": alpha,
            "Beta": beta,
            "Gamma": gamma,
            "Theta": theta
        }])
        #Scaling con scaler
        X_scaled = pd.DataFrame(scaler.transform(X), columns=["Alpha", "Beta", "Gamma", "Theta"])

        #Prediction
        preds = modello.predict(X_scaled) #è 0 o 1        


        return int(preds[0])
        #return 1
    except Exception as e:
        print(f"Python [model]: Exception occurred: {str(e)}")
        return 0  # Torna False in caso di errore per evitare crash


