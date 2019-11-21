package org.folio.ncip;


import org.apache.log4j.Logger;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.core.Promise;
import java.io.InputStream;
import java.util.Scanner;

import static org.folio.ncip.Constants.SYS_PORT;
import static org.folio.ncip.Constants.DEFAULT_PORT;


public class MainVerticle extends AbstractVerticle {

	private static final Logger logger = Logger.getLogger(MainVerticle.class);

	private static FolioNcipHelper folioNcipHelper;

	@Override
	public void start(Promise<Void> promise) throws Exception {

		final String portStr = System.getProperty(SYS_PORT, DEFAULT_PORT);
		final int port = Integer.parseInt(portStr);
		logger.info("Using port: " + port);		  
		folioNcipHelper  = new FolioNcipHelper(promise);
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());
		router.route(HttpMethod.POST, "/ncip").handler(this::handleNcip);
		router.route(HttpMethod.GET, "/ncipconfigcheck").handler(this::ncipConfigCheck);
		vertx.createHttpServer().requestHandler(router).listen(port);
	}


	protected void ncipConfigCheck(RoutingContext ctx) {
		logger.info("ncip mod - healthcheck method called");
		final Promise<Void> promise = Promise.promise();
		try {
			NcipConfigCheck ncipConfigCheck = new NcipConfigCheck(promise);
			ncipConfigCheck.process(ctx);
		}
		catch(Exception e) {
			logger.error("***************");
			logger.error(e.toString());
			ctx.response()
			.setStatusCode(500)
			.putHeader(HttpHeaders.CONTENT_TYPE, Constants.APP_XML) 
			//THIS REALLY SHOULD BE AN NCIP RESONSE THAT MIRRORS THE NCIP REQUEST TYPE (WITH PROBLEM ELEMENT) HOWEVER...
			//THAT IS NOT POSSIBLE IF WE'VE REACHED HERE BECAUSE ONLY THE MESSAGE HANDLER CAN CONSTRUCT A RESPONSE OBJECT
			//WE SHOULDN'T EVER GET HERE - FAMOUS LAST WORDS
			.end("<Problem><message>probem processing NCIP request</message><exception>" + e.toString()+ "</exception></Problem>");
		}
		ctx.response()
		.setStatusCode(200)
		.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain") //TODO CONSTANT
		.end("\"OK\"");
	}



	protected void handleNcip(RoutingContext ctx) {

		vertx.executeBlocking(promise -> {
			InputStream responseMsgInputStream = null;
			try {

				responseMsgInputStream = folioNcipHelper.ncipProcess(ctx);
			}
			catch(Exception e) {
				logger.error("error occured processing this request.  Unable to construct a proper NCIP response with problem element");
				logger.error(e.toString());
				ctx.response()
				.setStatusCode(500)
				.putHeader(HttpHeaders.CONTENT_TYPE, "application/xml") //TODO CONSTANT
				//THIS REALLY SHOULD BE AN NCIP RESONSE THAT MIRRORS THE NCIP REQUEST TYPE (WITH PROBLEM ELEMENT) HOWEVER...
				//THAT IS NOT POSSIBLE IF WE'VE REACHED HERE BECAUSE ONLY THE MESSAGE HANDLER CAN CONSTRUCT A RESPONSE OBJECT
				//WE SHOULDN'T EVER GET HERE IF THE MODULE IS SET UP PROPERLY - FAMOUS LAST WORDS
				.end("<Problem><message>probem processing NCIP request</message><exception>" + e.getLocalizedMessage() + "</exception></Problem>");
			}

			String inputStreamString = new Scanner(responseMsgInputStream,"UTF-8").useDelimiter("\\A").next();
			promise.complete(inputStreamString);
		}, res -> {
			System.out.println("The result is: " + res.result());
			ctx.response()
			.setStatusCode(200)
			.putHeader(HttpHeaders.CONTENT_TYPE, Constants.APP_XML) 
			.end(res.result().toString());
		});

	}


}
