import requests
import json
import urllib.parse
import numpy as np
import joblib
import warnings
import time
# Suppress warnings
warnings.filterwarnings('ignore')

# Load the pre-trained models and scaler
logistic_regression_model = joblib.load('LogisticRegression.joblib')
naive_bayes_model = joblib.load('GaussianNB.joblib')
scaler_train = joblib.load('scaler.joblib')

def handle_request(input_data):
    input_array = np.array(input_data)
    scaled_input_data = scaler_train.transform(input_array.reshape(1, -1))

    logistic_regression_prediction = logistic_regression_model.predict(scaled_input_data)
    naive_bayes_prediction = naive_bayes_model.predict(scaled_input_data)

    return logistic_regression_prediction, naive_bayes_prediction

while True:
    # Fetch the data from the server
    url = 'http://10.102.196.198:23500/unidirectionalFlows'
    response = requests.get(url)
    data = response.json()

    for i in data:
        input_data = [
            i['fwdFlow']['protocol'], 
            i['fwdFlow']['durationInMicroseconds'], 
            i['fwdFlow']['packets'], 
            i['bwdFlow']['packets'], 
            i['fwdFlow']['bytes'], 
            i['bwdFlow']['bytes'], 
            i['flowBytesPerSecond'], 
            i['flowPacketsPerSecond'], 
            i['bwdPacketLengthMax'], 
            i['bwdPacketLengthMin']
        ]
        
        lr, nb = handle_request(input_data)
        print(f"LR: {lr}, NB: {nb}, ID: {i['_id']}")

        # Construct the URL with _id as a query parameter
        put_url = f"http://10.102.196.198:23500/unidirectionalFlow/{urllib.parse.quote(i['fwdFlow']['srcIp'])}<->{urllib.parse.quote(i['fwdFlow']['dstIp'])}<->{urllib.parse.quote(str(i['fwdFlow']['protocol']))}"
        print(put_url)
        
        # Prepare the PUT request body
        data = {
            'nbPrediction': int(nb[0]), 
            'lrPrediction': int(lr[0])
        }
        
        # Use put_url instead of url for PUT request
        response = requests.put(put_url, json=data)

        # Print the response from the server
        print(f"Status Code: {response.status_code}")
        print(f"Response Body: {response.text}")

    time.sleep(5)
