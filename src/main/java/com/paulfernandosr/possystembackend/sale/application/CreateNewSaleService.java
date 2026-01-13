package com.paulfernandosr.possystembackend.sale.application;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.customer.domain.port.output.CustomerRepository;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleDocument;
import com.paulfernandosr.possystembackend.sale.domain.SaleStatus;
import com.paulfernandosr.possystembackend.sale.domain.exception.InvalidSaleException;
import com.paulfernandosr.possystembackend.sale.domain.port.input.CreateNewSaleUseCase;
import com.paulfernandosr.possystembackend.sale.domain.port.output.DocumentIssuer;
import com.paulfernandosr.possystembackend.sale.domain.port.output.SaleRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreateNewSaleService implements CreateNewSaleUseCase {
    private final SaleRepository saleRepository;
    private final DocumentIssuer documentIssuer;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public SaleDocument createNewSale(Sale sale) {
        User user = userRepository.findByUsername(sale.getIssuedBy().getUsername())
                .orElseThrow(() -> new InvalidSaleException("Invalid sale with user identification: " + sale.getIssuedBy().getUsername()));

        if (user.isNotOnRegister()) {
            throw new InvalidSaleException("Invalid sale with user identification: " + sale.getIssuedBy().getUsername());
        }

        sale.setIssuedBy(user);

        Customer customer = customerRepository.findById(sale.getCustomer().getId())
                .orElseThrow(() -> new InvalidSaleException("Invalid sale with customer identification: " + sale.getCustomer().getId()));

        sale.setCustomer(customer);

        sale.getItems().forEach(saleItem -> {
            Product foundProduct = productRepository.findById(saleItem.getProduct().getId())
                    .orElseThrow(() -> new InvalidSaleException("Invalid sale with product identification: " + saleItem.getProduct().getId()));

            saleItem.setProduct(foundProduct);
        });

        sale.setSerial(sale.getType().getSerial());
        sale.setNumber(saleRepository.getNextNumberByType(sale.getType()));
        sale.setIssuedAt(LocalDateTime.now());
        sale.setStatus(SaleStatus.PAID);

        saleRepository.create(sale);

        if (sale.isElectronic()) {
            return documentIssuer.issueElectronicDocument(sale);
        }

        return documentIssuer.issueSimpleDocument(sale);
    }
}
