package com.zuhlke.f10.corebank.account.service;

import com.zuhlke.f10.corebank.account.exception.AccountAlreadyExistException;
import com.zuhlke.f10.corebank.account.exception.ResourceNotFoundException;
import com.zuhlke.f10.corebank.account.repository.AccountRepository;
import com.zuhlke.f10.corebank.account.repository.TransactionRepository;
import com.zuhlke.f10.corebank.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public AccountServiceImpl() {
    }

    @Override
    public Account createAccount(Account account) {

        accountRepository.findByAccountNumber(account.getAccountNumber())
                .ifPresent((a) -> {
                    logger.warn("Account {}  already exists", a.getAccountNumber());
                    throw new AccountAlreadyExistException("409", "Account already exists");
                });


        Account savedAccount = accountRepository.save(account);

        /*
         * There has to be some initial money somewhere...;-)
         */
        if (account.getProductType().equals("FINANCING")) {
            initializeAccount(savedAccount, new BigDecimal("9000000000"));
        }

        return savedAccount;
    }

    @Override
    public void deleteAccount(String id) {
        accountRepository.deleteById(id);
    }

    @Override
    public Account getAccountById(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("404", "Account not found"));
    }

    @Override
    public Account getAccountByNumber(String accountNumber) {

        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("404", "Account not found"));
    }

    @Override
    public Account updateAccount(String id, Account account) {
        return accountRepository.save(account);
    }

    @Override
    public List<Account> listAccounts() {
        return accountRepository.findAll();
    }

    @Override
    public TransferResponse makeFundTransfer(String id, FundTransferDetail detail) {

        TransferResponse response = new TransferResponse();
        response.setReferenceId(id);

        //throw exception if account does not exists
        Account sourceAccount = getAccountById(id);

        //1. check if destination account exist
        Account destAccount = getAccountByNumber(detail.getCreditAccountNumber());

        //2. check balance of source account
        AccountBalance balance = getAccountBalance(id);
        if (balance.getBalance().compareTo(detail.getAmount()) < 0) {
            response.setStatus(TransferResponse.StatusEnum.REJECTED);
            response.setComment("Account has insufficient balance");
            return response;
        }

        //3. create debit transaction on source account
        Transaction debitTran = new Transaction();
        debitTran.setAccountId(sourceAccount.getId());
        debitTran.setAmount(detail.getAmount());
        debitTran.setCurrency(detail.getSourceCurrency());
        debitTran.setCreditDebitIndicator(Transaction.CreditDebitIndicatorEnum.DEBIT);
        debitTran.setTransactionCode("Fund Transfer");
        debitTran.setTransactionReference(destAccount.getAccountNumber());
        debitTran.setValueDateTime(LocalDateTime.now());
        debitTran.setBookingDateTime(LocalDateTime.now());

        Transaction savedDebitTran = transactionRepository.save(debitTran);

        //4. create credit transaction on destination account
        Transaction creditTran = new Transaction();
        creditTran.setAccountId(destAccount.getId());
        creditTran.setAmount(detail.getAmount());
        creditTran.setCurrency(detail.getDestinationCurrency());
        creditTran.setCreditDebitIndicator(Transaction.CreditDebitIndicatorEnum.CREDIT);
        creditTran.setTransactionCode("Fund Transfer");
        creditTran.setTransactionReference(sourceAccount.getAccountNumber());
        creditTran.setValueDateTime(LocalDateTime.now());
        creditTran.setBookingDateTime(LocalDateTime.now());


        Transaction savedCreditTran = transactionRepository.save(creditTran);


        response.setStatus(TransferResponse.StatusEnum.ACCEPTED);
        response.setReferenceId(savedDebitTran.getId() + "-" + savedCreditTran.getId());
        response.setComment("Fund Transfer was Successfully processed");

        return response;
    }


    @Override
    public AccountBalance getAccountBalance(String id) {

        //throw exception if account does not exists
        Account account = getAccountById(id);

        //compute balance from transaction repository
        AccountTransactions accountTransactions = getAccountTransactions(id);
        List<Transaction> transactions = accountTransactions.getData();

        //1. Get sum of all credit transactions
        BigDecimal sumCredit = transactions.stream()
                .filter(t -> t.getCreditDebitIndicator().equals(Transaction.CreditDebitIndicatorEnum.CREDIT))
                .map(t -> t.getAmount())
                .reduce(BigDecimal::add).orElse(new BigDecimal(0));

        //2. Get sum of all debit transactions
        BigDecimal sumDebit = transactions.stream()
                .filter(t -> t.getCreditDebitIndicator().equals(Transaction.CreditDebitIndicatorEnum.DEBIT))
                .map(t -> t.getAmount())
                .reduce(BigDecimal::add).orElse(new BigDecimal(0));

        //3. balance = sum of credit - sum of debit
        BigDecimal calculatedBalance = sumCredit.subtract(sumDebit);

        AccountBalance balance = new AccountBalance();
        balance.setCurrency(account.getCurrency());
        balance.setBalance(calculatedBalance);
        balance.setAccountNumber(account.getAccountNumber());
        balance.setId(account.getId());
        balance.setBankCode(account.getBankCode());
        balance.setName(account.getName());
        balance.setProductType(account.getProductType());

        return balance;
    }

    @Override
    public AccountTransactions getAccountTransactions(String id) {

        AccountTransactions transactions = new AccountTransactions();

        Comparator<Transaction> transactionComparator = Comparator.comparing(Transaction::getBookingDateTime).reversed();
        List<Transaction> transactionList = transactionRepository.findByAccountId(id);
        transactionList.sort(transactionComparator);

        transactions.setData(transactionList);

        return transactions;
    }

    @Override
    public TransferResponse createTransaction(String id, Transaction transaction) {

        //throw exception if account does not exists
        getAccountById(id);

        transaction.setBookingDateTime(LocalDateTime.now());
        transaction.setValueDateTime(LocalDateTime.now());
        transaction.setId(UUID.randomUUID().toString());
        transaction.setAccountId(id);
        Transaction savedTransaction = transactionRepository.save(transaction);


        TransferResponse response = new TransferResponse();
        response.setReferenceId(savedTransaction.getId());
        response.setStatus(TransferResponse.StatusEnum.ACCEPTED);
        response.setComment("Transaction was Successfully processed");

        return response;
    }

    /**
     * This doesn't occur in real life, unfortunately. But here, we need something to start with
     * @param account the new account
     * @param amount the amount to initialize the account with
     */
    private void initializeAccount (Account account, BigDecimal amount) {

        Transaction debitTran = new Transaction();
        debitTran.setAccountId(account.getId());
        debitTran.setAmount(amount);
        debitTran.setCurrency("USD");
        debitTran.setCreditDebitIndicator(Transaction.CreditDebitIndicatorEnum.CREDIT);
        debitTran.setTransactionCode("Fund Transfer");
        debitTran.setTransactionReference("Initial amount");
        debitTran.setValueDateTime(LocalDateTime.now());
        debitTran.setBookingDateTime(LocalDateTime.now());

        transactionRepository.save(debitTran);
   }
}
