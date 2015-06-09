package com.bandwidth.sdk.examples;

import com.bandwidth.sdk.BandwidthClient;
import com.bandwidth.sdk.model.Message;
import com.bandwidth.sdk.model.ReceiptRequest;
import com.bandwidth.sdk.model.events.SmsEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@WebServlet(urlPatterns = {SendSmsServlet.RECEIPT_RES, SendSmsServlet.CALLBACK_RES, SendSmsServlet.STATUS_RES},
            loadOnStartup = 1, asyncSupported = true)
public class SendSmsServlet extends HttpServlet {

    public static final String RECEIPT_RES = "/demo/receipt";

    public static final String CALLBACK_RES = "/demo/receipt/callback";

    public static final String STATUS_RES = "/demo/receipt/status";

    private Map<String, List<SmsEvent>> callbackEvents = new ConcurrentHashMap<>();

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getServletPath().equals(RECEIPT_RES)) {
            sendSmsWithReceipt(request, response);

        } else if (request.getServletPath().equals(CALLBACK_RES)) {
            receiveSmsCallback(request, response);

        } else if (request.getServletPath().equals(STATUS_RES)) {
            startAsyncStatus(request, response);
        }
    }

    private void sendSmsWithReceipt(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // There are two ways to set your credentials, e.g. your App Platform userId, API Token and API Secret
        // you can set these as environment variables or set them with the
        // BandwidthClient.getInstance(userId, apiToken, apiSecret) method.
        //
        // Use the setenv.sh script to set the env variables
        // BANDWIDTH_USER_ID
        // BANDWIDTH_API_TOKEN
        // BANDWIDTH_API_SECRET
        //
        // or uncomment the following lines and set them here
        // BandwidthClient bandwidthClient = BandwidthClient.getInstance();
        // bandwidthClient.setCredentials("user_id", "token", "secret");

        final String toNumber = request.getParameter("toNumber");
        if (StringUtils.isBlank(toNumber)) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        String fromNumber = System.getenv().get("ALLOCATED_NUMBER");
        if (fromNumber == null) {
            fromNumber = System.getProperty("ALLOCATED_NUMBER");
        }

        if (StringUtils.isBlank(fromNumber)) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        try {
            final String messageText =  "SMS with receipt requested! What up from App Platform";

            final Map<String, Object> params = new HashMap<String, Object>();
            params.put("to", toNumber);
            params.put("from", fromNumber);
            params.put("text", messageText);
            params.put("receiptRequested", ReceiptRequest.ALL.toString());
            params.put("callbackUrl", request.getRequestURL().append("/callback").toString());
            params.put("callbackHttpMethod", "GET");

            final Message message = Message.create(params);

            // Write page to follow callback statuses
            PrintWriter writer = response.getWriter();
            writer.write(createSmsStatusPage(request, message));
            writer.flush();

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveSmsCallback(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final String messageId = request.getParameter("messageId");
        if (messageId != null) {

            List<SmsEvent> listEvents = null;

            synchronized (this) {
                listEvents = callbackEvents.get(messageId);
                if (listEvents == null) {
                    listEvents = new ArrayList<SmsEvent>(2);
                    callbackEvents.put(messageId, listEvents);
                }
            }
            listEvents.add(getSmsEvent(request));
        }
    }

    private void startAsyncStatus(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final String messageId = request.getParameter("messageId");

        if (messageId == null) {
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        final AsyncContext asyncContext = request.startAsync();
        asyncContext.getResponse().setContentType("text/event-stream");
        asyncContext.getResponse().setCharacterEncoding("UTF-8");

        executor.scheduleAtFixedRate(new Runnable() {

            public void run() {
                try {
                    List<SmsEvent> events = callbackEvents.get(messageId);
                    if (events == null || events.isEmpty()) {
                        return;
                    }

                    ServletResponse response = asyncContext.getResponse();

                    PrintWriter printWriter = response.getWriter();
                    printWriter.write(events.size() < 2 ? "event:sms-callback-event\n" : "event:complete-sms-callback\n");
                    printWriter.write("data:" + formatSmsEvent(events) + "\n\n");
                    printWriter.flush();

                    if (events.size() >= 2) {
                        asyncContext.complete();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private String createSmsStatusPage(final HttpServletRequest request, final Message message) {
        VelocityEngine ve = new VelocityEngine();
        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        ve.init();

        Template template = ve.getTemplate("/sms-status-page.vm");
        Context context = new VelocityContext();
        context.put("message", message);
        context.put("callbackEvent", "sms-callback-event");
        context.put("completeEvent", "complete-sms-callback");
        context.put("statusUrl", request.getRequestURL().append("/status").append("?messageId=")
                .append(message.getId()));

        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        return writer.toString();
    }

    private String formatSmsEvent(final List<SmsEvent> events) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            SmsEvent smsEvent = events.get(i);
            builder.append(i == 0 ? "{" : ",{");

            builder.append("\"messageId\":").append("\"").append(smsEvent.getMessageId()).append("\",");
            builder.append("\"direction\":").append("\"").append(smsEvent.getDirection()).append("\",");
            builder.append("\"from\":").append("\"").append(smsEvent.getFrom()).append("\",");
            builder.append("\"to\":").append("\"").append(smsEvent.getTo()).append("\",");
            builder.append("\"text\":").append("\"").append(smsEvent.getText()).append("\",");
            builder.append("\"state\":").append("\"").append(smsEvent.getState()).append("\",");

            if (smsEvent.getDeliveryState() != null) {
                builder.append("\"deliveryState\":").append("\"").append(smsEvent.getDeliveryState()).append("\",");
            }
            if (smsEvent.getDeliveryCode() != null) {
                builder.append("\"deliveryCode\":").append("\"").append(smsEvent.getDeliveryCode()).append("\",");
            }
            if (smsEvent.getDeliveryDescription() != null) {
                builder.append("\"deliveryDescription\":").append("\"").append(smsEvent.getDeliveryDescription()).append("\",");
            }

            builder.append("\"messageUri\":").append("\"").append(smsEvent.getMessageUri()).append("\"");
            builder.append("}");
        }
        return builder.append("]").toString();
    }

    private SmsEvent getSmsEvent(HttpServletRequest request) {
        try {
            final StringBuilder builder = new StringBuilder("{");

            for (String key : request.getParameterMap().keySet()) {
                builder.append("\"").append(key).append("\"").append(":").append("\"")
                        .append(request.getParameterMap().get(key)[0]).append("\",");
            }
            builder.append("}");
            return (SmsEvent) SmsEvent.createEventFromString(builder.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
