package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractCustomerProfileRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractGuarantorRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractInstallmentRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractItemRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractQueryRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractVehicleSpecsRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresContractQueryRepository implements ContractQueryRepository {

    private final JdbcClient jdbcClient;

    private final ContractItemRepository contractItemRepository;
    private final ContractInstallmentRepository contractInstallmentRepository;
    private final ContractGuarantorRepository contractGuarantorRepository;
    private final ContractCustomerProfileRepository contractCustomerProfileRepository;
    private final ContractVehicleSpecsRepository contractVehicleSpecsRepository;

    @Override
    public long count(String likeParam, String status) {

        StringBuilder sb = new StringBuilder();
        sb.append("""
            SELECT COUNT(*)
              FROM contract c
              LEFT JOIN contract_item ci ON ci.contract_id = c.id
              LEFT JOIN product_serial_unit psu ON psu.id = ci.serial_unit_id
             WHERE (
                    c.customer_doc_number ILIKE ?
                 OR c.customer_name ILIKE ?
                 OR (c.series || '-' || c.number::text) ILIKE ?
                 OR COALESCE(psu.vin,'') ILIKE ?
             )
        """);

        if (status != null && !status.isBlank()) {
            sb.append(" AND c.status = ? ");
            return jdbcClient.sql(sb.toString())
                    .params(likeParam, likeParam, likeParam, likeParam, status)
                    .query(Long.class)
                    .single();
        }

        return jdbcClient.sql(sb.toString())
                .params(likeParam, likeParam, likeParam, likeParam)
                .query(Long.class)
                .single();
    }

    @Override
    public List<ContractSummaryResponse> findPage(String likeParam, String status, int limit, int offset) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            SELECT c.id AS contractId,
                   c.series,
                   c.number,
                   c.issue_date AS issueDate,
                   c.customer_doc_number AS customerDocNumber,
                   c.customer_name AS customerName,
                   c.payment_type AS paymentType,
                   c.installments,
                   c.initial_amount AS initialAmount,
                   c.total_amount AS totalAmount,
                   c.status,
                   c.sale_id AS saleId,
                   ci.sku,
                   ci.description,
                   psu.vin
              FROM contract c
              LEFT JOIN contract_item ci ON ci.contract_id = c.id
              LEFT JOIN product_serial_unit psu ON psu.id = ci.serial_unit_id
             WHERE (
                    c.customer_doc_number ILIKE ?
                 OR c.customer_name ILIKE ?
                 OR (c.series || '-' || c.number::text) ILIKE ?
                 OR COALESCE(psu.vin,'') ILIKE ?
             )
        """);
        if (status != null && !status.isBlank()) {
            sb.append(" AND c.status = ? ");
        }
        sb.append(" ORDER BY c.id DESC LIMIT ? OFFSET ? ");

        if (status != null && !status.isBlank()) {
            return jdbcClient.sql(sb.toString())
                    .params(likeParam, likeParam, likeParam, likeParam, status, limit, offset)
                    .query(ContractSummaryResponse.class)
                    .list();
        }

        return jdbcClient.sql(sb.toString())
                .params(likeParam, likeParam, likeParam, likeParam, limit, offset)
                .query(ContractSummaryResponse.class)
                .list();
    }

    @Override
    public ContractDetailResponse findDetail(Long contractId) {
        String sql = """
            SELECT id AS contractId,
                   station_id AS stationId,
                   created_by AS createdBy,
                   series,
                   number,
                   issue_date AS issueDate,
                   currency,
                   exchange_rate AS exchangeRate,
                   price_list AS priceList,
                   customer_id AS customerId,
                   customer_doc_type AS customerDocType,
                   customer_doc_number AS customerDocNumber,
                   customer_name AS customerName,
                   customer_address AS customerAddress,
                   payment_type AS paymentType,
                   cash_price AS cashPrice,
                   interest_rate_monthly AS interestRateMonthly,
                   installments,
                   initial_amount AS initialAmount,
                   financed_amount AS financedAmount,
                   interest_amount AS interestAmount,
                   total_amount AS totalAmount,
                   status,
                   sale_id AS saleId,
                   notes
              FROM contract
             WHERE id = ?
        """;

        ContractDetailResponse header = jdbcClient.sql(sql)
                .param(contractId)
                .query(ContractDetailResponse.class)
                .optional()
                .orElse(null);

        if (header == null) return null;

        var item = contractItemRepository.findByContractId(contractId);
        if (item != null) {
            header.setItem(ContractDetailResponse.ContractItemDetail.builder()
                    .productId(item.getProductId())
                    .serialUnitId(item.getSerialUnitId())
                    .sku(item.getSku())
                    .description(item.getDescription())
                    .brand(item.getBrand())
                    .model(item.getModel())
                    .vin(item.getVin())
                    .unitPrice(item.getUnitPrice())
                    .build());
        }

        
        // ✅ datos de la unidad física (VIN / chasis / motor / color / año)
        if (item != null && item.getSerialUnitId() != null && header.getItem() != null) {
            String sqlUnit = """
                SELECT vin,
                       chassis_number AS chassisNumber,
                       engine_number AS engineNumber,
                       color,
                       year_make AS yearMake
                  FROM product_serial_unit
                 WHERE id = ?
            """;

            Object[] unit = jdbcClient.sql(sqlUnit)
                    .param(item.getSerialUnitId())
                    .query((rs, rowNum) -> new Object[]{
                            rs.getString("vin"),
                            rs.getString("chassisNumber"),
                            rs.getString("engineNumber"),
                            rs.getString("color"),
                            (Integer) rs.getObject("yearMake")
                    })
                    .optional()
                    .orElse(null);

            if (unit != null) {
                header.getItem().setVin((String) unit[0]);
                header.getItem().setChassisNumber((String) unit[1]);
                header.getItem().setEngineNumber((String) unit[2]);
                header.getItem().setColor((String) unit[3]);
                header.getItem().setYearMake((Integer) unit[4]);
            }
        }

// ✅ ficha técnica del vehículo (si existe)
        if (item != null && item.getProductId() != null) {
            var vs = contractVehicleSpecsRepository.findByProductId(item.getProductId());
            if (vs != null) {
                header.setVehicleSpecs(VehicleSpecsDto.builder()
                        .productId(vs.getProductId())
                        .vehicleType(vs.getVehicleType())
                        .bodywork(vs.getBodywork())
                        .engineCapacity(vs.getEngineCapacity())
                        .fuel(vs.getFuel())
                        .cylinders(vs.getCylinders())
                        .netWeight(vs.getNetWeight())
                        .payload(vs.getPayload())
                        .grossWeight(vs.getGrossWeight())
                        .vehicleClass(vs.getVehicleClass())
                        .enginePower(vs.getEnginePower())
                        .rollingForm(vs.getRollingForm())
                        .seats(vs.getSeats())
                        .passengers(vs.getPassengers())
                        .axles(vs.getAxles())
                        .wheels(vs.getWheels())
                        .length(vs.getLength())
                        .width(vs.getWidth())
                        .height(vs.getHeight())
                        .build());
            }
        }

        var ins = contractInstallmentRepository.findByContractId(contractId);
        header.setInstallmentsDetail(ins.stream().map(x -> ContractInstallmentResponse.builder()
                .installmentNumber(x.getInstallmentNumber())
                .dueDate(x.getDueDate())
                .amount(x.getAmount())
                .paidAmount(x.getPaidAmount())
                .paidAt(x.getPaidAt())
                .paidByUsername(x.getPaidByUsername())
                .status(x.getStatus())
                .build()).toList());

        var g = contractGuarantorRepository.findByContractId(contractId);
        if (g != null) {
            header.setGuarantor(ContractGuarantorDto.builder()
                    .docType(g.getDocType())
                    .docNumber(g.getDocNumber())
                    .fullName(g.getFullName())
                    .address(g.getAddress())
                    .phone(g.getPhone())
                    .occupation(g.getOccupation())
                    .companyName(g.getCompanyName())
                    .monthlyIncome(g.getMonthlyIncome())
                    .build());
        }

        var p = contractCustomerProfileRepository.findByContractId(contractId);
        if (p != null) {
            header.setCustomerProfile(ContractCustomerProfileDto.builder()
                    .maritalStatus(p.getMaritalStatus())
                    .nationality(p.getNationality())
                    .district(p.getDistrict())
                    .province(p.getProvince())
                    .housingType(p.getHousingType())
                    .rentAmount(p.getRentAmount())
                    .employerName(p.getEmployerName())
                    .employerAddress(p.getEmployerAddress())
                    .employmentTime(p.getEmploymentTime())
                    .netIncome(p.getNetIncome())
                    .spouseIncome(p.getSpouseIncome())
                    .otherIncome(p.getOtherIncome())
                    .totalIncome(p.getTotalIncome())
                    .customerReferences(p.getCustomerReferences())
                    .build());
        }

        return header;
    }
}
