package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.customer.domain.exception.CustomerAlreadyExistsException;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.customer.domain.port.input.CreateNewCustomerUseCase;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CreateNewCustomerService implements CreateNewCustomerUseCase {
    private final CustomerRepository customerRepository;

    @Override
    public Customer createNewCustomer(Customer customer) {
        validateRequiredCustomerData(customer);
        normalizeCustomer(customer);
        validateManualCustomerData(customer);
        normalizeOptionalAddress(customer);

        boolean doesCustomerExists = customerRepository.existsByDocument(
                customer.getDocumentType(),
                customer.getDocumentNumber()
        );

        if (doesCustomerExists) {
            throw new CustomerAlreadyExistsException(
                    "Customer already exists with document: "
                            + customer.getDocumentType() + " " + customer.getDocumentNumber()
            );
        }

        customerRepository.create(customer);
        return customer;
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
        customer.setGivenNames(upper(customer.getGivenNames()));
        customer.setLastName(upper(customer.getLastName()));
        customer.setSecondLastName(upper(customer.getSecondLastName()));
        customer.setLegalName(upper(customer.getLegalName()));
        customer.setAddress(upper(customer.getAddress()));
        customer.setUbigeo(trim(customer.getUbigeo()));
        customer.setDepartment(upper(customer.getDepartment()));
        customer.setProvince(upper(customer.getProvince()));
        customer.setDistrict(upper(customer.getDistrict()));

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

    private void normalizeOptionalAddress(Customer customer) {
        boolean hasMainAddress = !isBlank(customer.getAddress());
        List<CustomerAddress> addresses = customer.getAddresses();

        if (!hasMainAddress && (addresses == null || addresses.isEmpty())) {
            return;
        }

        if (hasMainAddress) {
            validateUbigeoData(
                    customer.getAddress(),
                    customer.getUbigeo(),
                    customer.getDepartment(),
                    customer.getProvince(),
                    customer.getDistrict()
            );

            if (addresses == null || addresses.isEmpty()) {
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
        }

        boolean hasFiscal = false;
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

            address.setEnabled(true);
            address.setPosition(address.isFiscal() ? 0 : ++position);
            hasFiscal = hasFiscal || address.isFiscal();
        }

        if (!hasFiscal && hasMainAddress) {
            for (CustomerAddress address : addresses) {
                if (address == null || isBlank(address.getAddress())) {
                    continue;
                }
                address.setFiscal(true);
                address.setPosition(0);
                break;
            }
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
