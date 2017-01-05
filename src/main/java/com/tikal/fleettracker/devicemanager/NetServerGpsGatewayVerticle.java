package com.tikal.fleettracker.devicemanager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import com.cyngn.kafka.produce.MessageProducer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.json.JsonObject;

public class NetServerGpsGatewayVerticle extends AbstractVerticle {
	private final SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmmss");

	private boolean createReceptionTime;

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NetServerGpsGatewayVerticle.class);

    private String topic;

	@Override
	public void start() {
	    topic = config().getJsonObject("producer").getString("default_topic");
		vertx.createNetServer().connectHandler(this::handleConnection).listen(config().getInteger("netserver-port"));		
		logger.info("Started Net Server on port {} to listen for GPS", config().getInteger("netserver-port"));
	}
	
	private void handleConnection(final NetSocket ns) {
		ns.handler(RecordParser.newDelimited("\n",this::handleSocketData))
		.exceptionHandler(t->logger.error("Oops, something went wrong: ", t))
		.closeHandler(v->logger.warn("remove.sockets :", ns.writeHandlerID()));
	}
	
	private void handleSocketData(final Buffer b) {
		final String bufferPayload = b.toString();
		final String[] split = validateData(bufferPayload);
		if(split==null)
			return;
		
		final String identifiedPayload = addIdAndReceptionTime(bufferPayload);
		logger.debug("Sending the GPS Reading:{}", identifiedPayload);
		JsonObject json = new JsonObject();
		json.put("value", identifiedPayload);
		json.put("type", 2);
		json.put("topic", topic);
		logger.debug("Sending the GPS Json:{}", json);
      	vertx.eventBus().send(MessageProducer.EVENTBUS_DEFAULT_ADDRESS,json );
	}


	private String[] validateData(final String bufferPayload) {
		if(bufferPayload==null || !bufferPayload.contains(",")){
			logger.error("Got bad gps in buffer: "+bufferPayload);
			return null;
		}
		final String[] split = bufferPayload.split(",");
		if(split.length<4){
			logger.error("Got bad gps in buffer: "+bufferPayload);
			return null;
		}
		return split;
	}
	
	
	private String addIdAndReceptionTime(final String gpsPayload) {
		String identifiedPauload;
		if(createReceptionTime)
			identifiedPauload = gpsPayload+","+Long.valueOf(df.format(new Date()));
		identifiedPauload = gpsPayload+","+UUID.randomUUID();
		return identifiedPauload;
	}

}
