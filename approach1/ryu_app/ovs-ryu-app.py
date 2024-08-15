from ryu.base import app_manager
from ryu.controller import ofp_event
from ryu.controller.handler import MAIN_DISPATCHER
from ryu.controller.handler import set_ev_cls
from ryu.ofproto import ofproto_v1_3
import subprocess
from ryu.lib.packet import packet
import binascii
import requests
import json


# Send all GTP traffic to the controller
# =================================================================================================================================
# Build switch flows
commands = [
    # Normal Switching
    'sudo ovs-ofctl -O OpenFlow13 add-flow br-ovs-ryu "table=0,cookie=0x0,priority=100,actions=normal"',
    # Capture GTP Traffic
    'sudo ovs-ofctl -O OpenFlow13 add-flow br-ovs-ryu "table=0,cookie=0x1,priority=1000,udp,tp_dst=2152,actions=controller"',
]
for i, command in enumerate(commands):
    try:
        output = subprocess.check_output(command, shell=True)
        print(output.decode())
        print("Flow {} of {} added".format(i + 1, len(commands)))
    except subprocess.CalledProcessError as e:
        print(f"Error: {e}")

# Define the Ryu application
class L2Switch(app_manager.RyuApp):
    OFP_VERSIONS = [ofproto_v1_3.OFP_VERSION]

    def __init__(self, *args, **kwargs):
        super(L2Switch, self).__init__(*args, **kwargs)

    @set_ev_cls(ofp_event.EventOFPPacketIn, MAIN_DISPATCHER)
    def packet_in_handler(self, ev):
        msg = ev.msg
        dp = msg.datapath
        ofp = dp.ofproto
        ofp_parser = dp.ofproto_parser

        hex_data = binascii.hexlify(msg.data).decode('utf-8')

        # Initialize packets and extract details from the received packet
        pkt = packet.Packet(msg.data)

        # Extract source and destination IP addresses
        ue_src_ip_hex = msg.data[70:74]
        ue_dst_ip_hex = msg.data[74:78]

        # Convert the IP addresses to octets
        ue_src_ip = ".".join(map(str, ue_src_ip_hex))
        ue_dst_ip = ".".join(map(str, ue_dst_ip_hex))

        # Extract and convert the protocol to integer
        protocol = msg.data[67]

        # Get the size of the packet in bytes
        size = len(msg.data)

        # Check if the source IP is blocked
        blocked_ip_url = f"http://10.102.196.198:23000/blocked-ips/{ue_src_ip}"
        try:
            response = requests.get(blocked_ip_url)
            if response.status_code == 200:
                blocked_info = response.json()
                match = ofp_parser.OFPMatch(in_port=msg.match['in_port'])
                inst = [ofp_parser.OFPInstructionActions(ofp.OFPIT_CLEAR_ACTIONS, [])]
                mod = ofp_parser.OFPFlowMod(datapath=dp, buffer_id=ofp.OFP_NO_BUFFER, priority=1, match=match, instructions=inst)
                dp.send_msg(mod)
                return
            elif response.status_code == 404:
                print(f"Source IP {ue_src_ip} is not blocked.")

                # Send the packet out to the original destination
                actions = [ofp_parser.OFPActionOutput(ofp.OFPP_NORMAL, 0)]
                out = ofp_parser.OFPPacketOut(datapath=dp, buffer_id=msg.buffer_id, in_port=msg.match['in_port'], actions=actions, data=msg.data)
                dp.send_msg(out)

                # Update flow data
                flow_url = f"http://10.102.196.198:23000/flow/{ue_src_ip}-{ue_dst_ip}-{protocol}"
                flow_data = {
                    "bytes": size
                }
                try:
                    flow_response = requests.put(flow_url, data=json.dumps(flow_data), headers={'Content-Type': 'application/json'})
                    if flow_response.status_code == 200:
                        print("Flow data updated successfully.")
                    else:
                        print(f"Error updating flow data: {flow_response.status_code}")
                except requests.exceptions.RequestException as e:
                    print(f"HTTP request failed: {e}")

            else:
                print(f"Error checking blocked IP status: {response.status_code}")
        except requests.exceptions.RequestException as e:
            print(f"HTTP request failed: {e}")
