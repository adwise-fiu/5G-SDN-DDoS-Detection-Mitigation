package us.fiu.adwise.approach2;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import us.fiu.adwise.approach2.common.FabricDeviceConfig;
import us.fiu.adwise.approach2.pipeconf.PipeconfLoader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TableId;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.basics.SubjectFactories;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.config.ConfigFactory;
import static us.fiu.adwise.approach2.AppConstants.PIPECONF_ID;
import static us.fiu.adwise.approach2.AppConstants.APP_NAME;
import static us.fiu.adwise.approach2.AppConstants.CLEAN_UP_DELAY;
import static us.fiu.adwise.approach2.AppConstants.DEFAULT_CLEAN_UP_RETRY_TIMES;
import static us.fiu.adwise.approach2.common.Utils.sleep;
import us.fiu.adwise.approach2.common.Utils;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.pi.model.PiPipeconf;
import org.onosproject.net.pi.runtime.PiPacketOperation;
import org.onosproject.net.pi.service.PiPipeconfService;    
import org.onosproject.net.packet.PacketService;
//Ioexception
import java.io.IOException;
import java.io.OutputStream;



@Component(immediate = true)
public class CreateGTPFlows {
//GetFlow getFlowInstance = new GetFlow();
    private static final Logger log = LoggerFactory.getLogger(CreateGTPFlows.class);
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PiPipeconfService pipeconfService;
    private ApplicationId appId;
    private final PacketProcessor packetProcessor = new PacketInProcessor();
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;
    private DeviceId deviceId = DeviceId.deviceId("device:s1");
     //FlowRule List 
     public static List<FlowRule> flowRules = new ArrayList<FlowRule>();
    
    @Activate
    protected void activate() {
        appId = coreService.registerApplication("us.fiu.adwise.approach2.CreateGTPFlows");
        packetService.addProcessor(packetProcessor, PacketPriority.REACTIVE.priorityValue());
        TrafficSelector selector = DefaultTrafficSelector.emptySelector();
        packetService.requestPackets(selector, PacketPriority.REACTIVE, appId);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(packetProcessor);
        flowRuleService.removeFlowRulesById(appId);
        //Clear the flowRules list
        flowRules.clear();
        log.info("Stopped");
    }

    public static List<FlowRule> getFlowRules() {
        return flowRules;
    }


    private class PacketInProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
           // Packet In Processing
            DeviceId deviceId = context.inPacket().receivedFrom().deviceId();
            Ethernet eth = context.inPacket().parsed();
            ByteBuffer rawPacketData = context.inPacket().unparsed();
            byte[] rawDataBytes = rawPacketData.array();
            // Convert raw packet data to hexadecimal string
            String hexData = bytesToHex(rawDataBytes);

            //Packet length
            byte[] packetLength = Arrays.copyOfRange(rawDataBytes, 60,62);
            int packetLengthInt = ByteBuffer.wrap(packetLength).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
            String packetLengthString = "Packet Length:" + packetLengthInt;

            //protocol
            byte[] protocol = Arrays.copyOfRange(rawDataBytes, 67,68);
            int protocolInt = protocol[0]&0xFF;
            String protocolString = "Protocol:" + protocolInt;

            //source IP
            byte[] sourceIP = Arrays.copyOfRange(rawDataBytes, 70,74);
            String sourceIPString = "Source IP:" + bytesToHex(sourceIP);
            //Convert source IP to octets
            String sourceIPStringOctets = convertIPtoOctets(sourceIP);

            //destination IP
            byte[] destinationIP = Arrays.copyOfRange(rawDataBytes, 74,78);
            String destinationIPString = "Destination IP:" + bytesToHex(destinationIP);
            //Convert destination IP to octets
            String destinationIPStringOctets = convertIPtoOctets(destinationIP);

            //Add Forward Flow 
            final PiCriterion FwdFlowCriterion = PiCriterion.builder()
                .matchExact(PiMatchFieldId.of("hdr.inner_ipv4.src_addr"), sourceIP)
                .matchExact(PiMatchFieldId.of("hdr.inner_ipv4.dst_addr"), destinationIP)
                .matchExact(PiMatchFieldId.of("hdr.inner_ipv4.protocol"), protocol)
                .build();
            final PiAction FwdFlowAction;
            FwdFlowAction = PiAction.builder()
                        .withId(PiActionId.of("IngressPipeImpl.track_gtp_flows"))
                        //.withParameter(new PiActionParam(PiActionParamId.of("index"), flowIndex))
                        .build();

            // Build the FlowRule with the specified index
            final FlowRule FwdRule = Utils.buildFlowRule(deviceId, appId, "IngressPipeImpl.gtp_flows",FwdFlowCriterion, FwdFlowAction);

            // Add the FlowRule to the list of flow rules
            flowRules.add(FwdRule);

            // Add the FlowRule to the device
            flowRuleService.applyFlowRules(FwdRule);

            //Add Bwd Flow
            final PiCriterion BwdFlowCriterion = PiCriterion.builder()
                .matchExact(PiMatchFieldId.of("hdr.inner_ipv4.src_addr"), destinationIP)
                .matchExact(PiMatchFieldId.of("hdr.inner_ipv4.dst_addr"), sourceIP)
                .matchExact(PiMatchFieldId.of("hdr.inner_ipv4.protocol"), protocol)
                .build();

            final PiAction BwdFlowAction;
            BwdFlowAction = PiAction.builder()
                        .withId(PiActionId.of("IngressPipeImpl.track_gtp_flows"))
                        //.withParameter(new PiActionParam(PiActionParamId.of("index"), flowIndex))
                        .build();

            // Build the FlowRule with the specified index
            final FlowRule BwdRule = Utils.buildFlowRule(deviceId, appId, "IngressPipeImpl.gtp_flows",BwdFlowCriterion, BwdFlowAction);

            // Add the FlowRule to the list of flow rules
            flowRules.add(BwdRule);

            // Add the FlowRule to the device
            flowRuleService.applyFlowRules(BwdRule);
            String jsonData = prepareJsonData(packetLengthInt);
            updateDB(sourceIPStringOctets, destinationIPStringOctets, protocolInt, packetLengthInt);
        }


    }

    private String convertIPtoOctets(byte[] ip) {
        String[] octets = new String[4];
        for (int i = 0; i < 4; i++) {
            octets[i] = Integer.toString(ip[i] & 0xFF);
        }
        String ipString = octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
        return ipString;
    }


    private void updateDB(String srcIp, String dstIp, int protocol, int length) {
        try {

            String putUrl = "http://10.102.196.198:23500/flow/"+srcIp+"-"+dstIp+"-"+protocol;
            URL url = new URL(putUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String jsonData = prepareJsonData(length);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                log.info("Flow data posted successfully.");
            } else {
                log.info("Failed to post flow data. HTTP Response Code: " + responseCode);
                // Read the error message from the server
                InputStream errorStream = connection.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                log.info("Server error message: " + response.toString());
            }
            connection.disconnect();
        } catch (Exception e) {
            log.info("Exception occurred while posting flows: " + e.getMessage());
            e.printStackTrace();  // Print the stack trace for more detailed error information
        }
    }

        private static String prepareJsonData(int length) {
        return String.format(
            "{ \"bytes\": %d , \"packets\": 1 }",
            length
        );
    }

    private void sendFlowMessagesToServer(String flowString) {
        try {
            // Convert the results to a JSON format
            String jsonResults = "{ \"Data\": \"" + flowString + "\" }";

            Socket socket = new Socket("10.102.196.198", 7000);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(jsonResults.getBytes());

            // Receive a response from the server (in this case, "1")
            InputStream inputStream = socket.getInputStream();
            byte[] responseBuffer = new byte[1];
            int bytesRead = inputStream.read(responseBuffer);

            socket.close();
        } catch (IOException e) {
            log.error("Error sending flow messages to the server", e);
        }
    }


    // Helper method to convert byte array to hexadecimal string
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

}
