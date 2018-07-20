package com.zuhlke.f10.insurance.quotes.service;

import com.zuhlke.f10.insurance.InsuranceConstants;
import com.zuhlke.f10.insurance.exception.ResourceNotFoundException;
import com.zuhlke.f10.insurance.model.*;
import com.zuhlke.f10.insurance.policies.repository.PolicyRepository;
import com.zuhlke.f10.insurance.products.repository.InvoiceRepository;
import com.zuhlke.f10.insurance.products.repository.ProductRepository;
import com.zuhlke.f10.insurance.products.service.ProductService;
import com.zuhlke.f10.insurance.quotes.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;


@Service
public class QuoteServiceImpl implements QuoteService {

    @Autowired
    private ProductRepository  productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private QuoteRepository  quoteRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PolicyRepository  policyRepository;

    @Override
    public QuoteDetails computeCost(String productId,  QuoteCriteria quoteCriteria) {

        Product product = productService.getProduct(productId);

        QuoteDetails quoteDetails = new QuoteDetails();
        quoteDetails.setQuoteId(UUID.randomUUID().toString());
        quoteDetails.setProductId(product.getId());
        quoteDetails.setProductDescription(product.getProductDescription());
        quoteDetails.setPremiumCurrency(product.getPriceCurrency());
        quoteDetails.setCoverageDates(quoteCriteria.getCoverageDate());
        quoteDetails.setAddOns(quoteCriteria.getAddOns());

        //compute number of days covered
        LocalDate from = quoteCriteria.getCoverageDate().getFrom();
        LocalDate to = quoteCriteria.getCoverageDate().getTo();

        long numDays = ChronoUnit.DAYS.between(from, to) + 1;
        BigDecimal cost = product.getPriceAmount().multiply(new BigDecimal(numDays));
        quoteDetails.setPremiumAmount(cost);
        quoteDetails.setNumberOfDaysCovered((int)numDays);

        //save quote
        quoteRepository.save(quoteDetails);

        return quoteDetails;
    }

    @Override
    public Invoice purchase(String quoteId, PurchaseDetails purchaseDetails) {

        //Get Quote from DB
        QuoteDetails quoteDetails = quoteRepository.findByQuoteId(quoteId)
                .orElseThrow(()->  new ResourceNotFoundException("404","Quote record not found"));

        //Get Product
        Product product = productRepository.findById(quoteDetails.getProductId())
                .orElseThrow(()->new ResourceNotFoundException("404","Product not found"));


        //create Invoice
        Invoice invoice = createInvoice(quoteId, purchaseDetails, quoteDetails, product);
        Invoice savedInvoice = invoiceRepository.save(invoice);


        //Create Policy Details
        PolicyDetails policyDetails = createPolicyDetails(savedInvoice);
        policyRepository.save(policyDetails);


        return savedInvoice;
    }

    private Invoice createInvoice(String quoteId, PurchaseDetails purchaseDetails, QuoteDetails quoteDetails, Product product) {

        Invoice invoice = new Invoice();
        invoice.setReferenceNumber(quoteId);

        //create invoice details
        InvoiceDetails details = createInvoiceDetails(purchaseDetails, quoteDetails);


        invoice.setTaxInvoice(details);
        return invoice;
    }

    private InvoiceDetails createInvoiceDetails(PurchaseDetails purchaseDetails, QuoteDetails quoteDetails) {

        //Premium details
        PremiumDetails premiumDetails = createPremiumDetails(quoteDetails);


        InvoiceDetails details = new InvoiceDetails();
        details.setPremiumDetails(premiumDetails);

        //Billing address
        BillingAddress address = new BillingAddress();
        address.setName(purchaseDetails.getPolicyHolder().getName());
        address.setMailingAddress(purchaseDetails.getPolicyHolder().getMailingAddress());
        details.billTo(address);

        //Coverage date
        details.setCoverageDate(quoteDetails.getCoverageDates());

        //Invoice Date
        details.setInvoiceDate(LocalDate.now());

        details.setPolicyId(UUID.randomUUID().toString());
        details.setProductDescription(quoteDetails.getProductDescription());
        details.setProviderId("100");
        details.setProviderName("Zuhlke Insurance");

        return details;
    }

    private PremiumDetails createPremiumDetails(QuoteDetails quoteDetails) {
        PremiumDetails premiumDetails = new PremiumDetails();
        premiumDetails.setCurrency(InsuranceConstants.DEFAULT_CURRENCY);
        premiumDetails.setLessDiscount(new BigDecimal(0));
        premiumDetails.setPremiumAmt(quoteDetails.getPremiumAmount());
        premiumDetails.setPremiumAmtBeforeTax(quoteDetails.getPremiumAmount());
        premiumDetails.setTaxAmt(new BigDecimal(0));
        premiumDetails.setTotalAmt(quoteDetails.getPremiumAmount());
        return premiumDetails;
    }

    private PolicyDetails createPolicyDetails(Invoice invoice) {
        PolicyDetails policyDetails = new PolicyDetails();
        policyDetails.setPolicyId(invoice.getReferenceNumber());
        policyDetails.setPremiumAmount(invoice.getTaxInvoice().getPremiumDetails().getPremiumAmt());
        policyDetails.setPremiumCurrency(invoice.getTaxInvoice().getPremiumDetails().getCurrency());
        policyDetails.setProductId(invoice.getTaxInvoice().getProductId());

        PolicyBenefit benefit = new PolicyBenefit();
        benefit.setName("Benefits");
        benefit.description("Cool benefits");
        policyDetails.addBenefitsItem(benefit);
        return policyDetails;
    }

}
