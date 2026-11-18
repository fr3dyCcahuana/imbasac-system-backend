package com.paulfernandosr.possystembackend.customer.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** A deterministic revision of persisted data, independent of JSON ordering. */
public final class CustomerEditRevision {
    private CustomerEditRevision() {
    }

    public static String calculate(Customer customer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, "customer-edit-v1");
            add(digest, customer.getId(), customer.getLegalName(), customer.getDocumentType(),
                    customer.getDocumentNumber(), customer.getPhone(), customer.getEmail(),
                    customer.getGivenNames(), customer.getLastName(), customer.getSecondLastName(),
                    customer.getAddress(), customer.getUbigeo(), customer.getDepartment(),
                    customer.getProvince(), customer.getDistrict(), customer.getSunatStatus(),
                    customer.getSunatCondition(), customer.getStreetType(), customer.getStreetName(),
                    customer.getZoneCode(), customer.getZoneType(), customer.getAddressNumber(),
                    customer.getInterior(), customer.getLot(), customer.getApartment(),
                    customer.getBlock(), customer.getKilometer(), customer.isRetentionAgent(),
                    customer.isGoodContributor(), customer.getSunatType(), customer.getEconomicActivity(),
                    customer.getNumberOfEmployees(), customer.getBillingType(),
                    customer.getAccountingType(), customer.getForeignTrade(), customer.isEnabled());

            List<CustomerAddress> addresses = customer.getAddresses() == null ? List.of()
                    : customer.getAddresses().stream().filter(Objects::nonNull)
                    .sorted(Comparator.comparing(CustomerAddress::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            add(digest, addresses.size());
            for (CustomerAddress address : addresses) {
                add(digest, address.getId(), address.getCustomerId(), address.getAddress(),
                        address.getUbigeo(), address.getDepartment(), address.getProvince(),
                        address.getDistrict(), address.getPhone(), address.getEmail(),
                        address.isFiscal(), address.isEnabled(), address.getPosition(),
                        address.getLatitude(), address.getLongitude(), address.getGeolocationStatus(),
                        address.getGeolocationSource(), address.getGeolocationAccuracyMeters(),
                        address.getGeolocatedAt(), address.getGeolocatedBy(),
                        address.getGeolocationVerifiedAt(), address.getGeolocationVerifiedBy(),
                        address.getGeolocationAddressHash(), address.getUpdatedAt());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void add(MessageDigest digest, Object... values) {
        for (Object value : values) {
            if (value == null) {
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
                continue;
            }
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
    }
}
