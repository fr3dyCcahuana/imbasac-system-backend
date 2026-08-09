package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerNotFoundException;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerAssignmentRequest;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerAssignmentResponse;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerCommercialInfoResponse;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerContactUpdateResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerCommercialService {
    private final JdbcClient jdbcClient;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public CustomerCommercialInfoResponse getCommercialInfo(Long customerId) {
        Customer customer = getCustomer(customerId);
        return buildCommercialInfo(customer);
    }

    @Transactional
    public CustomerContactUpdateResponse updateContact(Long customerId, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            throw new InvalidCustomerException("Contact patch is required");
        }

        Customer customer = getCustomer(customerId);
        String phone = customer.getPhone();
        String email = customer.getEmail();

        if (patch.containsKey("phone")) {
            phone = normalizePhone(asString(patch.get("phone")));
        }

        if (patch.containsKey("email")) {
            email = normalizeEmail(asString(patch.get("email")));
        }

        customerRepository.updateContact(customerId, phone, email);
        return CustomerContactUpdateResponse.builder()
                .customerId(customerId)
                .phone(phone)
                .email(email)
                .build();
    }

    public CustomerAssignmentResponse getActiveAssignment(Long customerId) {
        getCustomer(customerId);
        return findActiveAssignment(customerId).orElse(null);
    }

    public List<CustomerAssignmentResponse> getAssignments(Long customerId) {
        getCustomer(customerId);
        String sql = assignmentSelectSql() + """
                WHERE ca.customer_id = ?
                ORDER BY ca.assigned_at DESC, ca.id DESC
                """;

        return jdbcClient.sql(sql)
                .param(customerId)
                .query(this::mapAssignment)
                .list();
    }

    @Transactional
    public CustomerAssignmentResponse assign(Long customerId, CustomerAssignmentRequest request, Principal principal) {
        Long actorUserId = currentUser(principal).getId();
        Long responsibleUserId = validateResponsibleUser(request);
        lockCustomer(customerId);

        if (findActiveAssignment(customerId).isPresent()) {
            throw new InvalidCustomerException("Customer already has an active commercial responsible");
        }

        insertAssignment(customerId, responsibleUserId, actorUserId, reason(request));
        return findActiveAssignment(customerId)
                .orElseThrow(() -> new InvalidCustomerException("Customer commercial assignment could not be created"));
    }

    @Transactional
    public CustomerAssignmentResponse reassign(Long customerId, CustomerAssignmentRequest request, Principal principal) {
        Long actorUserId = currentUser(principal).getId();
        Long responsibleUserId = validateResponsibleUser(request);
        lockCustomer(customerId);

        CustomerAssignmentResponse current = findActiveAssignment(customerId)
                .orElseThrow(() -> new InvalidCustomerException("Customer does not have an active commercial responsible"));

        if (responsibleUserId.equals(current.getUserId())) {
            throw new InvalidCustomerException("New responsible must be different from current responsible");
        }

        closeAssignment(current.getId(), actorUserId, reason(request));
        insertAssignment(customerId, responsibleUserId, actorUserId, reason(request));

        return findActiveAssignment(customerId)
                .orElseThrow(() -> new InvalidCustomerException("Customer commercial reassignment could not be created"));
    }

    @Transactional
    public void unassign(Long customerId, CustomerAssignmentRequest request, Principal principal) {
        Long actorUserId = currentUser(principal).getId();
        lockCustomer(customerId);

        CustomerAssignmentResponse current = findActiveAssignment(customerId)
                .orElseThrow(() -> new InvalidCustomerException("Customer does not have an active commercial responsible"));

        closeAssignment(current.getId(), actorUserId, reason(request));
    }

    private CustomerCommercialInfoResponse buildCommercialInfo(Customer customer) {
        return CustomerCommercialInfoResponse.builder()
                .customerId(customer.getId())
                .legalName(customer.getLegalName())
                .documentType(customer.getDocumentType() == null ? null : customer.getDocumentType().name())
                .documentNumber(customer.getDocumentNumber())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .activeAssignment(findActiveAssignment(customer.getId()).orElse(null))
                .build();
    }

    private Customer getCustomer(Long customerId) {
        if (customerId == null) {
            throw new InvalidCustomerException("Customer id is required");
        }

        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with identification: " + customerId));
    }

    private User currentUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    private Long validateResponsibleUser(CustomerAssignmentRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new InvalidCustomerException("Commercial responsible user is required");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new InvalidCustomerException("Commercial responsible user does not exist"));

        if (!user.isEnabled()) {
            throw new InvalidCustomerException("Commercial responsible user is disabled");
        }

        return user.getId();
    }

    private void lockCustomer(Long customerId) {
        String sql = "SELECT id FROM customers WHERE id = ? FOR UPDATE";
        Optional<Long> locked = jdbcClient.sql(sql)
                .param(customerId)
                .query(Long.class)
                .optional();

        if (locked.isEmpty()) {
            throw new CustomerNotFoundException("Customer not found with identification: " + customerId);
        }
    }

    private Optional<CustomerAssignmentResponse> findActiveAssignment(Long customerId) {
        String sql = assignmentSelectSql() + """
                WHERE ca.customer_id = ?
                  AND ca.active = TRUE
                LIMIT 1
                """;

        return jdbcClient.sql(sql)
                .param(customerId)
                .query(this::mapAssignment)
                .optional();
    }

    private void insertAssignment(Long customerId, Long responsibleUserId, Long actorUserId, String reason) {
        String sql = """
                INSERT INTO customer_assignments(
                    customer_id,
                    user_id,
                    assigned_by,
                    assigned_at,
                    active,
                    reason
                ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, TRUE, ?)
                """;

        try {
            jdbcClient.sql(sql)
                    .params(customerId, responsibleUserId, actorUserId, reason)
                    .update();
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidCustomerException("Customer already has an active commercial responsible");
        }
    }

    private void closeAssignment(Long assignmentId, Long actorUserId, String reason) {
        String sql = """
                UPDATE customer_assignments
                SET active = FALSE,
                    unassigned_by = ?,
                    unassigned_at = CURRENT_TIMESTAMP,
                    reason = COALESCE(?, reason)
                WHERE id = ?
                  AND active = TRUE
                """;

        int updated = jdbcClient.sql(sql)
                .params(actorUserId, reason, assignmentId)
                .update();

        if (updated != 1) {
            throw new InvalidCustomerException("Customer commercial assignment is no longer active");
        }
    }

    private String assignmentSelectSql() {
        return """
                SELECT
                    ca.id,
                    ca.customer_id,
                    ca.user_id,
                    responsible.first_name AS user_first_name,
                    responsible.last_name AS user_last_name,
                    responsible.username AS username,
                    ca.assigned_by,
                    trim(concat(COALESCE(assigned_by_user.first_name, ''), ' ', COALESCE(assigned_by_user.last_name, ''))) AS assigned_by_name,
                    ca.assigned_at,
                    ca.unassigned_by,
                    trim(concat(COALESCE(unassigned_by_user.first_name, ''), ' ', COALESCE(unassigned_by_user.last_name, ''))) AS unassigned_by_name,
                    ca.unassigned_at,
                    ca.active,
                    ca.reason
                FROM customer_assignments ca
                INNER JOIN users responsible ON responsible.id = ca.user_id
                INNER JOIN users assigned_by_user ON assigned_by_user.id = ca.assigned_by
                LEFT JOIN users unassigned_by_user ON unassigned_by_user.id = ca.unassigned_by
                """;
    }

    private CustomerAssignmentResponse mapAssignment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return CustomerAssignmentResponse.builder()
                .id(rs.getLong("id"))
                .customerId(rs.getLong("customer_id"))
                .userId(rs.getLong("user_id"))
                .userFirstName(rs.getString("user_first_name"))
                .userLastName(rs.getString("user_last_name"))
                .username(rs.getString("username"))
                .assignedBy(rs.getLong("assigned_by"))
                .assignedByName(nullIfBlank(rs.getString("assigned_by_name")))
                .assignedAt(toLocalDateTime(rs.getTimestamp("assigned_at")))
                .unassignedBy(getNullableLong(rs, "unassigned_by"))
                .unassignedByName(nullIfBlank(rs.getString("unassigned_by_name")))
                .unassignedAt(toLocalDateTime(rs.getTimestamp("unassigned_at")))
                .active(rs.getBoolean("active"))
                .reason(rs.getString("reason"))
                .build();
    }

    private Long getNullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String reason(CustomerAssignmentRequest request) {
        return request == null ? null : nullIfBlank(request.getReason());
    }

    private String normalizePhone(String value) {
        String trimmed = nullIfBlank(value);
        if (trimmed == null) return null;
        String normalized = trimmed.replaceAll("[\\s()-]", "");
        if (!normalized.matches("\\+?\\d{6,15}")) {
            throw new InvalidCustomerException("Customer phone must have between 6 and 15 digits");
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String trimmed = nullIfBlank(value);
        if (trimmed == null) return null;
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidCustomerException("Customer email has invalid format");
        }
        return normalized;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullIfBlank(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
