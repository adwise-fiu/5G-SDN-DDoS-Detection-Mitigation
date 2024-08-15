import requests
import time
from mitigation import mitigate  # Import the mitigate function

# URL to get the flows
get_url = "http://10.102.196.198:23000/unidirectionalFlows"

def detect_and_send_to_mitigation():
    response = requests.get(get_url)
    if response.status_code == 200:
        flows = response.json()
        ids = []

        for flow in flows:
            ids.append(flow['_id'])

            if flow['nbPredictionAvg'] == 1 and flow['lrPredictionAvg'] == 1:
                print(f"Flow {flow['_id']} is malicious")
                # Call the mitigation module
                mitigate(flow['_id'], ids)  # This will now work since it's imported
    else:
        print(f"Failed to get flows: {response.text}")

if __name__ == "__main__":
    while True:
        detect_and_send_to_mitigation()
        time.sleep(30)
