package com.paulfernandosr.possystembackend.salev2.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.proformav2.domain.exception.InvalidProformaV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.exception.SaleCreateRejectedException;
import com.paulfernandosr.possystembackend.salev2.domain.exception.SaleIdempotencyConflictException;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleCreateIdempotencyRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.stockreservation.domain.exception.StockReservationException;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.UUID;

/** HTTP create boundary: replay must happen before mutable sale/session/stock validations. */
@Service
@RequiredArgsConstructor
public class CreateIdempotentSaleV2Service {
    private final CreateSaleV2UseCase createSale;
    private final UserRepository users;
    private final SaleCreateIdempotencyRepository idempotency;
    private final ObjectMapper mapper;

    @Transactional
    public SaleV2DocumentResponse create(SaleV2CreateRequest request, String username) {
        // Preserve compatibility for existing integrations that do not yet send a key.
        if (request == null || request.getIdempotencyKey() == null) {
            return createSale.create(request, username);
        }
        UUID key = parseKey(request.getIdempotencyKey());
        User user = users.findByUsername(username)
                .orElseThrow(() -> new InvalidSaleV2Exception("Usuario inválido: " + username));
        String requestHash = requestHash(request);
        SaleCreateIdempotencyRepository.Entry entry = idempotency.acquire(user.getId(), key, requestHash);
        if (!requestHash.equals(entry.requestHash())) {
            throw new SaleIdempotencyConflictException(
                    "Este intento de registro ya se utilizó con otros datos. "
                            + "Revisa el historial de ventas antes de iniciar una nueva venta.");
        }
        try {
            if (entry.responseJson() != null) {
                return mapper.readValue(entry.responseJson(), SaleV2DocumentResponse.class);
            }
            // The sale service normalizes fields and filters proforma items. Keep the original
            // request unchanged so hashing always represents exactly what the caller submitted.
            SaleV2CreateRequest saleRequest = mapper.convertValue(request, SaleV2CreateRequest.class);
            SaleV2DocumentResponse response;
            try {
                response = createSale.create(saleRequest, username);
            } catch (InvalidSaleV2Exception | StockReservationException | InvalidCustomerException
                     | InvalidProformaV2Exception rejection) {
                throw new SaleCreateRejectedException(key, rejection);
            }
            if (response == null || response.getSaleId() == null) {
                throw new IllegalStateException("La venta no devolvió su identificador de registro.");
            }
            idempotency.complete(user.getId(), key, response.getSaleId(), mapper.writeValueAsString(response));
            return response;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo recuperar el resultado del registro de venta.", exception);
        }
    }

    private static UUID parseKey(String rawKey) {
        try {
            UUID key = UUID.fromString(rawKey);
            if (!key.toString().equalsIgnoreCase(rawKey)) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException exception) {
            throw new InvalidSaleV2Exception("El identificador del intento de registro no es válido.");
        }
    }

    String requestHash(SaleV2CreateRequest request) {
        ObjectNode body = mapper.valueToTree(request);
        body.remove("idempotencyKey");
        try {
            byte[] canonical = mapper.writeValueAsBytes(canonicalize(body));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo identificar el intento de registro de venta.", exception);
        }
    }

    private JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            value.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> sorted.set(name, canonicalize(value.get(name))));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode array = mapper.createArrayNode();
            value.forEach(element -> array.add(canonicalize(element)));
            return array;
        }
        return value.isNumber() ? DecimalNode.valueOf(value.decimalValue().stripTrailingZeros()) : value;
    }
}
