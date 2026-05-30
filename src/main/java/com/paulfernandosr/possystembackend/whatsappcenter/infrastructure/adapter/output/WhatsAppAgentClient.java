package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.ManualModeRequest;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.SendManualMessageRequest;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.StartConversationRequest;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.VideoCallInviteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class WhatsAppAgentClient {
    private final RestClient restClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    /**
     * Inyectamos el RestClient.Builder autoconfigurado por Spring Boot para
     * heredar los HttpMessageConverters (Jackson, multipart, etc.). Si
     * construimos RestClient.builder() estaticamente perdemos los converters
     * y los POST con body POJO se envian sin serializar -> el AI agent
     * responde 422 "body required".
     */
    public WhatsAppAgentClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${app.whatsapp-agent.base-url:http://127.0.0.1:8092}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.restClient = builder
                .baseUrl(this.baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public JsonNode setManualMode(Long conversationId, ManualModeRequest request) {
        try {
            ManualModeRequest payloadObject = request != null ? request : new ManualModeRequest();
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("enabled", payloadObject.getEnabled() == null || payloadObject.getEnabled());
            if (payloadObject.getAdvisorId() != null) {
                payloadMap.put("advisorId", payloadObject.getAdvisorId());
            }
            if (payloadObject.getAdvisorName() != null && !payloadObject.getAdvisorName().isBlank()) {
                payloadMap.put("advisorName", payloadObject.getAdvisorName());
            }
            if (payloadObject.getNotes() != null && !payloadObject.getNotes().isBlank()) {
                payloadMap.put("notes", payloadObject.getNotes());
            }

            String payload = objectMapper.writeValueAsString(payloadMap);
            String path = "/whatsapp/conversations/" + conversationId + "/manual-mode";
            log.info("WhatsApp agent manual mode curl:\n{}", curl("POST", baseUrl + path, payload));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(30))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("WhatsApp agent manual mode response status={} body={}", response.statusCode(), response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), response.body());
            }
            return payload(objectMapper.readTree(response.body()));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar el cambio de modo manual para WhatsApp", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con el agente de WhatsApp en " + baseUrl + ". Verifica que imbasac-ai-agent-backend este levantado en el puerto 8092.",
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpio el cambio de modo manual en WhatsApp", ex);
        }
    }

    public JsonNode startConversation(StartConversationRequest request) {
        try {
            StartConversationRequest payloadObject = request != null ? request : new StartConversationRequest();
            String payload = objectMapper.writeValueAsString(payloadObject);
            String path = "/whatsapp/conversations/start";
            log.info("WhatsApp agent start conversation curl:\n{}", curl("POST", baseUrl + path, payload));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(45))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("WhatsApp agent start conversation response status={} body={}", response.statusCode(), response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), response.body());
            }
            return payload(objectMapper.readTree(response.body()));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar el inicio de conversacion para WhatsApp", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con el agente de WhatsApp en " + baseUrl + ". Verifica que imbasac-ai-agent-backend este levantado en el puerto 8092.",
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpio el inicio de conversacion en WhatsApp", ex);
        }
    }

    public JsonNode sendText(Long conversationId, SendManualMessageRequest request) {
        try {
            String text = request == null || request.getBody() == null ? "" : request.getBody().trim();
            Map<String, String> messagePayload = new LinkedHashMap<>();
            messagePayload.put("body", text);
            messagePayload.put("text", text);
            messagePayload.put("message", text);
            String payload = objectMapper.writeValueAsString(messagePayload);
            String path = "/whatsapp/conversations/" + conversationId + "/messages/text";
            log.info("WhatsApp agent send text curl:\n{}", curl("POST", baseUrl + path, payload));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(30))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("WhatsApp agent send text response status={} body={}", response.statusCode(), response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), response.body());
            }
            return payload(objectMapper.readTree(response.body()));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar el mensaje para WhatsApp", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con el agente de WhatsApp en " + baseUrl + ". Verifica que imbasac-ai-agent-backend este levantado en el puerto 8092.",
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpio el envio al agente de WhatsApp", ex);
        }
    }

    public JsonNode sendMedia(Long conversationId, MultipartFile file, String caption) {
        try {
            String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "archivo"
                    : file.getOriginalFilename();
            String contentType = file.getContentType() == null || file.getContentType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : file.getContentType();
            String cleanCaption = caption == null ? "" : caption;
            String boundary = "----IMBASAC-" + UUID.randomUUID();
            byte[] body = multipartBody(boundary, filename, contentType, file.getBytes(), cleanCaption);
            String path = "/whatsapp/conversations/" + conversationId + "/messages/media";

            log.info("WhatsApp agent send media curl:\n{}", curlMultipart(baseUrl + path, filename, contentType, cleanCaption));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(90))
                    .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("WhatsApp agent send media response status={} body={}", response.statusCode(), response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), response.body());
            }
            return payload(objectMapper.readTree(response.body()));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer la respuesta del agente de WhatsApp", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo enviar el archivo al agente de WhatsApp en " + baseUrl + ".",
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpio el envio del archivo al agente de WhatsApp", ex);
        }
    }

    public JsonNode inviteVideoCall(Long conversationId, VideoCallInviteRequest request) {
        try {
            VideoCallInviteRequest payloadObject = request != null ? request : new VideoCallInviteRequest();
            String payload = objectMapper.writeValueAsString(payloadObject);
            String path = "/whatsapp/conversations/" + conversationId + "/video-call/invite";
            log.info("WhatsApp agent video call invite curl:\n{}", curl("POST", baseUrl + path, payload));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(30))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("WhatsApp agent video call invite response status={} body={}", response.statusCode(), response.body());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), response.body());
            }
            return payload(objectMapper.readTree(response.body()));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar la invitacion de videollamada para WhatsApp", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con el agente de WhatsApp en " + baseUrl + ". Verifica que imbasac-ai-agent-backend este levantado en el puerto 8092.",
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpio la invitacion de videollamada en WhatsApp", ex);
        }
    }

    public ResponseEntity<byte[]> media(String mediaId) {
        return restClient.get()
                .uri("/whatsapp/media/{mediaId}", mediaId)
                .retrieve()
                .toEntity(byte[].class);
    }

    private JsonNode payload(JsonNode response) {
        if (response == null) {
            return null;
        }
        return response.has("payload") ? response.get("payload") : response;
    }

    private String curl(String method, String url, String jsonPayload) {
        return "curl --request " + method + " \\\n"
                + "  --url " + shellQuote(url) + " \\\n"
                + "  --header " + shellQuote("content-type: application/json") + " \\\n"
                + "  --data " + shellQuote(jsonPayload);
    }

    private String curlMultipart(String url, String filename, String contentType, String caption) {
        return "curl --request POST \\\n"
                + "  --url " + shellQuote(url) + " \\\n"
                + "  --form " + shellQuote("file=@" + filename + ";type=" + contentType) + " \\\n"
                + "  --form " + shellQuote("caption=" + (caption == null ? "" : caption));
    }

    private byte[] multipartBody(String boundary, String filename, String contentType, byte[] content, String caption) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writePartHeader(output, boundary, "file", filename, contentType);
        output.write(content);
        write(output, "\r\n");
        writePartHeader(output, boundary, "caption", null, null);
        write(output, caption == null ? "" : caption);
        write(output, "\r\n--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private void writePartHeader(ByteArrayOutputStream output, String boundary, String name, String filename, String contentType) throws IOException {
        write(output, "--" + boundary + "\r\n");
        String disposition = "Content-Disposition: form-data; name=\"" + escapeHeader(name) + "\"";
        if (filename != null) {
            disposition += "; filename=\"" + escapeHeader(filename) + "\"";
        }
        write(output, disposition + "\r\n");
        if (contentType != null) {
            write(output, "Content-Type: " + contentType + "\r\n");
        }
        write(output, "\r\n");
    }

    private void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeHeader(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String shellQuote(String value) {
        return "'" + (value == null ? "" : value).replace("'", "'\"'\"'") + "'";
    }

    private String trimTrailingSlash(String value) {
        String text = value == null || value.isBlank() ? "http://127.0.0.1:8092" : value.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
