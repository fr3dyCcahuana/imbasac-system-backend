package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerAlreadyExistsException;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerNotFoundException;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.customer.domain.port.input.UpdateCustomerUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UpdateCustomerService implements UpdateCustomerUseCase {
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public Customer updateCustomer(Long customerId, Customer customer) {
        if (customerId == null) {
            throw new InvalidCustomerException("Customer id is required");
        }

        Customer existingCustomer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with identification: " + customerId));

        validateRequiredCustomerData(customer);
        boolean preserveExistingAddresses = isBlank(customer.getAddress()) && customer.getAddresses() == null;

        normalizeCustomer(customer);
        preserveContactIfMissing(customer, existingCustomer);
        validateManualCustomerData(customer);

        if (preserveExistingAddresses) {
            copyMainAddressFromExistingCustomer(customer, existingCustomer);
            customer.setAddresses(existingCustomer.getAddresses());
        } else {
            normalizeAddressesAndSyncMainAddress(customer);
        }

        if (customerRepository.existsByDocumentExcludingId(
                customer.getDocumentType(),
                customer.getDocumentNumber(),
                customerId
        )) {
            throw new CustomerAlreadyExistsException(
                    "Customer already exists with document: "
                            + customer.getDocumentType() + " " + customer.getDocumentNumber()
            );
        }

        customer.setId(customerId);

        customerRepository.update(customer);
        customerRepository.replaceAddresses(customerId, customer.getAddresses());

        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with identification: " + customerId));
    }

    private void validateRequiredCustomerData(Customer customer) {
        if (customer == null) {
            throw new InvalidCustomerException("Customer is required");
        }

        if (customer.getDocumentType() == null) {
            throw new InvalidCustomerException("Document type is required");
        }

        if (isBlank(customer.getDocumentNumber())) {
            throw new InvalidCustomerException("Document number is required");
        }
    }

    private void normalizeCustomer(Customer customer) {
        customer.setDocumentNumber(trim(customer.getDocumentNumber()));
        customer.setPhone(normalizePhone(customer.getPhone()));
        customer.setEmail(normalizeEmail(customer.getEmail()));
        customer.setGivenNames(upper(customer.getGivenNames()));
        customer.setLastName(upper(customer.getLastName()));
        customer.setSecondLastName(upper(customer.getSecondLastName()));
        customer.setLegalName(upper(customer.getLegalName()));
        customer.setAddress(upper(customer.getAddress()));
        customer.setUbigeo(trim(customer.getUbigeo()));
        customer.setDepartment(upper(customer.getDepartment()));
        customer.setProvince(upper(customer.getProvince()));
        customer.setDistrict(upper(customer.getDistrict()));
        customer.setSunatStatus(upper(customer.getSunatStatus()));
        customer.setSunatCondition(upper(customer.getSunatCondition()));
        customer.setStreetType(upper(customer.getStreetType()));
        customer.setStreetName(upper(customer.getStreetName()));
        customer.setZoneCode(upper(customer.getZoneCode()));
        customer.setZoneType(upper(customer.getZoneType()));
        customer.setAddressNumber(upper(customer.getAddressNumber()));
        customer.setInterior(upper(customer.getInterior()));
        customer.setLot(upper(customer.getLot()));
        customer.setApartment(upper(customer.getApartment()));
        customer.setBlock(upper(customer.getBlock()));
        customer.setKilometer(upper(customer.getKilometer()));
        customer.setSunatType(upper(customer.getSunatType()));
        customer.setEconomicActivity(upper(customer.getEconomicActivity()));
        customer.setNumberOfEmployees(upper(customer.getNumberOfEmployees()));
        customer.setBillingType(upper(customer.getBillingType()));
        customer.setAccountingType(upper(customer.getAccountingType()));
        customer.setForeignTrade(upper(customer.getForeignTrade()));

        if (isNaturalPerson(customer.getDocumentType())) {
            customer.setLegalName(buildNaturalPersonLegalName(customer));
        }
    }

    private void validateManualCustomerData(Customer customer) {
        if (isNaturalPerson(customer.getDocumentType())) {
            if (isBlank(customer.getGivenNames())) {
                throw new InvalidCustomerException("Customer names are required");
            }
            if (isBlank(customer.getLastName())) {
                throw new InvalidCustomerException("Customer paternal last name is required");
            }
            if (isBlank(customer.getSecondLastName())) {
                throw new InvalidCustomerException("Customer maternal last name is required");
            }
            if (isBlank(customer.getLegalName())) {
                customer.setLegalName(buildNaturalPersonLegalName(customer));
            }
            return;
        }

        if (customer.getDocumentType() == DocumentType.RUC && isBlank(customer.getLegalName())) {
            throw new InvalidCustomerException("Customer legal name is required");
        }
    }

    private void copyMainAddressFromExistingCustomer(Customer customer, Customer existingCustomer) {
        customer.setAddress(existingCustomer.getAddress());
        customer.setUbigeo(existingCustomer.getUbigeo());
        customer.setDepartment(existingCustomer.getDepartment());
        customer.setProvince(existingCustomer.getProvince());
        customer.setDistrict(existingCustomer.getDistrict());
    }

    private void normalizeAddressesAndSyncMainAddress(Customer customer) {
        List<CustomerAddress> addresses = customer.getAddresses();

        if ((addresses == null || addresses.isEmpty()) && !isBlank(customer.getAddress())) {
            validateUbigeoData(
                    customer.getAddress(),
                    customer.getUbigeo(),
                    customer.getDepartment(),
                    customer.getProvince(),
                    customer.getDistrict()
            );

            addresses = new ArrayList<>();
            addresses.add(CustomerAddress.builder()
                    .address(customer.getAddress())
                    .ubigeo(customer.getUbigeo())
                    .department(customer.getDepartment())
                    .province(customer.getProvince())
                    .district(customer.getDistrict())
                    .fiscal(true)
                    .enabled(true)
                    .position(0)
                    .build());
            customer.setAddresses(addresses);
            return;
        }

        if (addresses == null) {
            return;
        }

        List<CustomerAddress> normalizedAddresses = new ArrayList<>();
        Set<String> uniqueAddresses = new HashSet<>();
        int fiscalCount = 0;
        int position = 0;

        for (CustomerAddress address : addresses) {
            if (address == null || isBlank(address.getAddress())) {
                continue;
            }

            address.setAddress(upper(address.getAddress()));
            address.setUbigeo(trim(address.getUbigeo()));
            address.setDepartment(upper(address.getDepartment()));
            address.setProvince(upper(address.getProvince()));
            address.setDistrict(upper(address.getDistrict()));

            validateUbigeoData(
                    address.getAddress(),
                    address.getUbigeo(),
                    address.getDepartment(),
                    address.getProvince(),
                    address.getDistrict()
            );

            String uniqueKey = address.getAddress().toLowerCase(Locale.ROOT) + "|" + nullSafe(address.getUbigeo());
            if (!uniqueAddresses.add(uniqueKey)) {
                throw new InvalidCustomerException("Customer has duplicated addresses in request");
            }

            address.setEnabled(true);

            if (address.isFiscal()) {
                fiscalCount++;
                address.setPosition(0);
            } else {
                address.setPosition(++position);
            }

            normalizedAddresses.add(address);
        }

        if (fiscalCount > 1) {
            throw new InvalidCustomerException("Customer can only have one fiscal address");
        }

        if (fiscalCount == 0 && !normalizedAddresses.isEmpty()) {
            CustomerAddress firstAddress = normalizedAddresses.get(0);
            firstAddress.setFiscal(true);
            firstAddress.setPosition(0);
        }

        normalizedAddresses.sort((a, b) -> {
            if (a.isFiscal() != b.isFiscal()) return a.isFiscal() ? -1 : 1;
            return Integer.compare(a.getPosition(), b.getPosition());
        });

        customer.setAddresses(normalizedAddresses);
        syncMainAddressFromFiscal(customer);
    }

    private void syncMainAddressFromFiscal(Customer customer) {
        if (customer.getAddresses() == null) return;

        for (CustomerAddress address : customer.getAddresses()) {
            if (!address.isFiscal()) continue;

            customer.setAddress(address.getAddress());
            customer.setUbigeo(address.getUbigeo());
            customer.setDepartment(address.getDepartment());
            customer.setProvince(address.getProvince());
            customer.setDistrict(address.getDistrict());
            return;
        }
    }

    private void validateUbigeoData(String address, String ubigeo, String department, String province, String district) {
        if (isBlank(address)) return;

        if (isBlank(ubigeo) || ubigeo.length() != 6 || !ubigeo.matches("\\d{6}")) {
            throw new InvalidCustomerException("Customer address ubigeo must have 6 digits");
        }
        if (isBlank(department)) {
            throw new InvalidCustomerException("Customer address department is required");
        }
        if (isBlank(province)) {
            throw new InvalidCustomerException("Customer address province is required");
        }
        if (isBlank(district)) {
            throw new InvalidCustomerException("Customer address district is required");
        }
    }

    private boolean isNaturalPerson(DocumentType documentType) {
        return documentType == DocumentType.DNI
                || documentType == DocumentType.CE
                || documentType == DocumentType.PASSPORT;
    }

    private String buildNaturalPersonLegalName(Customer customer) {
        return join(customer.getLastName(), customer.getSecondLastName(), customer.getGivenNames());
    }

    private String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (isBlank(value)) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String upper(String value) {
        String trimmed = trim(value);
        return isBlank(trimmed) ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private void preserveContactIfMissing(Customer customer, Customer existingCustomer) {
        if (isBlank(customer.getPhone())) {
            customer.setPhone(existingCustomer.getPhone());
        }
        if (isBlank(customer.getEmail())) {
            customer.setEmail(existingCustomer.getEmail());
        }
    }

    private String normalizePhone(String value) {
        String trimmed = trim(value);
        if (isBlank(trimmed)) return null;
        String normalized = trimmed.replaceAll("[\\s()-]", "");
        if (!normalized.matches("\\+?\\d{6,15}")) {
            throw new InvalidCustomerException("Customer phone must have between 6 and 15 digits");
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String trimmed = trim(value);
        if (isBlank(trimmed)) return null;
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidCustomerException("Customer email has invalid format");
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
