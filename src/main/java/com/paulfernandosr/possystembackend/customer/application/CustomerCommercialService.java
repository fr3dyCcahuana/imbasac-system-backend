package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
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
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerCommercialService {
    private final JdbcClient jdbcClient;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CustomerAddressContactValidator contactValidator;

    public CustomerCommercialInfoResponse getCommercialInfo(Long customerId) {
        Customer customer = getCustomer(customerId);
        return buildCommercialInfo(customer, findMainAddress(customer).orElse(null));
    }

    public CustomerCommercialInfoResponse getCommercialInfo(Long customerId, Long addressId) {
        Customer customer = getCustomer(customerId);
        return buildCommercialInfo(customer, findAddress(customer, addressId).orElse(null));
    }

    @Transactional
    public CustomerContactUpdateResponse updateContact(Long customerId, Map<String, Object> patch) {
        Customer customer = getCustomer(customerId);
        CustomerAddress address = findMainAddress(customer)
                .orElseThrow(() -> new InvalidCustomerException("Customer does not have a main address"));
        return updateAddressContact(customerId, address.getId(), patch);
    }

    @Transactional
    public CustomerContactUpdateResponse updateAddressContact(Long customerId, Long addressId, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            throw new InvalidCustomerException("Contact patch is required");
        }

        CustomerAddress address = lockAddress(customerId, addressId);
        String phone = address.getPhone();
        String email = address.getEmail();

        if (patch.containsKey("phone")) {
            phone = contactValidator.normalizeOptionalPeruvianMobile(asString(patch.get("phone")));
        }

        if (patch.containsKey("email")) {
            email = contactValidator.normalizeOptionalEmail(asString(patch.get("email")));
        }

        updateAddressContactRow(customerId, addressId, phone, email);
        return CustomerContactUpdateResponse.builder()
                .customerId(customerId)
                .addressId(addressId)
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

    @Transactional
    public CustomerCommercialInfoResponse completeMissingCommercialInfo(
            Long customerId,
            Long addressId,
            Map<String, Object> patch,
            Principal principal
    ) {
        Long actorUserId = currentUser(principal).getId();
        lockCustomer(customerId);
        CustomerAddress address = lockAddress(customerId, addressId);
        CustomerAssignmentResponse currentAssignment = findActiveAssignment(customerId).orElse(null);

        String nextPhone = address.getPhone();
        String nextEmail = address.getEmail();

        boolean phoneMissing = isBlank(nextPhone);
        boolean emailMissing = isBlank(nextEmail);

        if (phoneMissing && patch != null && patch.containsKey("phone")) {
            nextPhone = contactValidator.normalizeOptionalPeruvianMobile(asString(patch.get("phone")));
        } else if (patch != null && patch.containsKey("phone")) {
            String requestedPhone = contactValidator.normalizeOptionalPeruvianMobile(asString(patch.get("phone")));
            if (requestedPhone != null && !requestedPhone.equals(nextPhone)) {
                throw new InvalidCustomerException("El telefono de la direccion ya esta registrado y no puede modificarse desde este modal");
            }
        }

        if (emailMissing && patch != null && patch.containsKey("email")) {
            nextEmail = contactValidator.normalizeOptionalEmail(asString(patch.get("email")));
        } else if (!emailMissing && patch != null && patch.containsKey("email")) {
            String requestedEmail = contactValidator.normalizeOptionalEmail(asString(patch.get("email")));
            if (requestedEmail != null && !requestedEmail.equals(nextEmail)) {
                throw new InvalidCustomerException("El correo de la direccion ya esta registrado y no puede modificarse desde este modal");
            }
        }

        if (phoneMissing || (emailMissing && nextEmail != null)) {
            updateAddressContactRow(customerId, addressId, nextPhone, nextEmail);
        }

        Long responsibleUserId = patch == null ? null : asLong(patch.get("responsibleUserId"));
        if (currentAssignment == null && responsibleUserId != null) {
            User responsible = userRepository.findById(responsibleUserId)
                    .orElseThrow(() -> new InvalidCustomerException("Commercial responsible user does not exist"));
            if (!responsible.isEnabled()) {
                throw new InvalidCustomerException("Commercial responsible user is disabled");
            }
            insertAssignment(customerId, responsible.getId(), actorUserId, reasonFromPatch(patch));
        } else if (currentAssignment != null && responsibleUserId != null && !responsibleUserId.equals(currentAssignment.getUserId())) {
            throw new InvalidCustomerException("El responsable comercial ya esta registrado y no puede modificarse desde este modal");
        }

        Customer customer = getCustomer(customerId);
        return buildCommercialInfo(customer, findAddress(customer, addressId).orElse(null));
    }

    private CustomerCommercialInfoResponse buildCommercialInfo(Customer customer, CustomerAddress selectedAddress) {
        return CustomerCommercialInfoResponse.builder()
                .customerId(customer.getId())
                .legalName(customer.getLegalName())
                .documentType(customer.getDocumentType() == null ? null : customer.getDocumentType().name())
                .documentNumber(customer.getDocumentNumber())
                .selectedAddress(mapSelectedAddress(selectedAddress))
                .activeAssignment(findActiveAssignment(customer.getId()).orElse(null))
                .genericCustomer(false)
                .build();
    }

    private CustomerCommercialInfoResponse.SelectedAddress mapSelectedAddress(CustomerAddress address) {
        if (address == null) {
            return null;
        }

        return CustomerCommercialInfoResponse.SelectedAddress.builder()
                .addressId(address.getId())
                .addressText(address.getAddress())
                .ubigeo(address.getUbigeo())
                .department(address.getDepartment())
                .province(address.getProvince())
                .district(address.getDistrict())
                .main(address.isFiscal())
                .phone(address.getPhone())
                .email(address.getEmail())
                .build();
    }

    private Optional<CustomerAddress> findMainAddress(Customer customer) {
        if (customer == null || customer.getAddresses() == null) {
            return Optional.empty();
        }

        return customer.getAddresses().stream()
                .filter(address -> address != null && address.isEnabled())
                .sorted((a, b) -> {
                    int fiscalSort = Boolean.compare(b.isFiscal(), a.isFiscal());
                    if (fiscalSort != 0) {
                        return fiscalSort;
                    }
                    int positionSort = Integer.compare(a.getPosition(), b.getPosition());
                    if (positionSort != 0) {
                        return positionSort;
                    }
                    return Long.compare(a.getId() == null ? Long.MAX_VALUE : a.getId(), b.getId() == null ? Long.MAX_VALUE : b.getId());
                })
                .findFirst();
    }

    private Optional<CustomerAddress> findAddress(Customer customer, Long addressId) {
        if (customer == null || customer.getAddresses() == null || addressId == null) {
            return Optional.empty();
        }

        return customer.getAddresses().stream()
                .filter(address -> address != null && address.isEnabled())
                .filter(address -> addressId.equals(address.getId()))
                .findFirst();
    }

    private CustomerAddress lockAddress(Long customerId, Long addressId) {
        if (customerId == null) {
            throw new InvalidCustomerException("Customer id is required");
        }
        if (addressId == null) {
            throw new InvalidCustomerException("Customer address id is required");
        }

        String sql = """
                SELECT id, customer_id, address, ubigeo, department, province, district,
                       phone, email, fiscal, enabled, position
                FROM customer_address
                WHERE id = ?
                  AND customer_id = ?
                  AND enabled = TRUE
                FOR UPDATE
                """;

        return jdbcClient.sql(sql)
                .params(addressId, customerId)
                .query(CustomerAddress.class)
                .optional()
                .orElseThrow(() -> new InvalidCustomerException("La direccion no pertenece al cliente"));
    }

    private void updateAddressContactRow(Long customerId, Long addressId, String phone, String email) {
        int updated = jdbcClient.sql("""
                UPDATE customer_address
                SET phone = ?,
                    email = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND customer_id = ?
                  AND enabled = TRUE
                """)
                .params(phone, email, addressId, customerId)
                .update();

        if (updated != 1) {
            throw new InvalidCustomerException("La direccion no pertenece al cliente");
        }
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = nullIfBlank(String.valueOf(value));
        return text == null ? null : Long.valueOf(text);
    }

    private String reasonFromPatch(Map<String, Object> patch) {
        return patch == null ? null : nullIfBlank(asString(patch.get("reason")));
    }

    private String nullIfBlank(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
