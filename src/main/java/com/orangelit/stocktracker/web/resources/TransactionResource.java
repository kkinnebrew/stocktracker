package com.orangelit.stocktracker.web.resources;

import com.google.inject.Inject;
import com.googlecode.htmleasy.RedirectException;
import com.googlecode.htmleasy.View;
import com.orangelit.stocktracker.accounting.managers.AccountingManager;
import com.orangelit.stocktracker.accounting.models.Account;
import com.orangelit.stocktracker.accounting.models.Transaction;
import com.orangelit.stocktracker.accounting.models.TransactionLine;
import com.orangelit.stocktracker.accounting.models.TransactionType;
import com.orangelit.stocktracker.authentication.models.User;
import com.orangelit.stocktracker.common.exceptions.InvalidInputException;
import com.orangelit.stocktracker.common.exceptions.ItemNotFoundException;
import com.orangelit.stocktracker.web.dtos.AccountTransactionDTO;
import com.orangelit.stocktracker.web.views.TransactionAdminView;
import org.apache.commons.lang.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.*;

@Path("/transactions")
public class TransactionResource {

    @Inject
    private AccountingManager accountingManager;

    @GET
    @Path("/")
    public View get(@Context HttpServletRequest request, @QueryParam("accountId") String accountId) throws ItemNotFoundException, InvalidInputException
    {
        if (request.getSession().getAttribute("user") == null)
        {
            throw new RedirectException("/auth/login");
        }

        User user = (User)request.getSession().getAttribute("user");

        TransactionAdminView model = new TransactionAdminView();
        model.accounts = accountingManager.getAccounts(user.getUserId());
        model.user = (User)request.getSession().getAttribute("user");
        model.transactions = new LinkedList<>();
        model.transactionTypes = new LinkedList<>();

        if (StringUtils.isEmpty(accountId) && model.accounts.size() > 0) {
            throw new RedirectException("/transactions?accountId=" + model.accounts.get(0).getAccountId());
        } else if (StringUtils.isEmpty(accountId)) {
            return new View("/transactions.jsp", model);
        }

        Account account = accountingManager.getAccount(accountId);

        model.balance = accountingManager.getBalanceForAccount(accountId);
        model.account = account;
        model.transactionTypes = accountingManager.getTransactionTypes();

        List<Transaction> transactions = accountingManager.getTransactions(accountId);

        Collections.sort(transactions, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction o1, Transaction o2) {
                return o1.getTransactionDate().compareTo(o2.getTransactionDate());
            }
        });

        BigDecimal balance = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            List<Account> accounts = new LinkedList<>();
            for (TransactionLine line : transaction.getTransactionLines()) {
                if (!line.getAccount().getAccountId().equals(accountId)) {
                    accounts.add(line.getAccount());
                }
            }
            for (TransactionLine line : transaction.getTransactionLines()) {
                if (!line.getAccount().getAccountId().equals(accountId)) {
                    continue;
                }
                BigDecimal amount = BigDecimal.ZERO;
                if (account.getAccountType().getAccountCategory().getDirection()) {
                    amount = amount.add(line.getDebitAmount());
                    amount = amount.subtract(line.getCreditAmount());
                } else {
                    amount = amount.subtract(line.getDebitAmount());
                    amount = amount.add(line.getCreditAmount());
                }
                balance = balance.add(amount);
                model.transactions.add(new AccountTransactionDTO(
                        transaction.getTransactionId(),
                        transaction.getTransactionDate(),
                        transaction.getTransactionType(),
                        accounts,
                        transaction.getDescription(),
                        amount,
                        balance)
                );
            }
        }

        Collections.reverse(model.transactions);

        return new View("/transactions.jsp", model);

    }

    @POST
    @Path("/transfer")
    public Response post(@Context HttpServletRequest request,
                         @FormParam("debitAccountId") String debitAccountId,
                         @FormParam("creditAccountId") String creditAccountId,
                         @FormParam("transactionTypeId") String transactionTypeId,
                         @FormParam("transactionDate") Date transactionDate,
                         @FormParam("amount") String amount,
                         @FormParam("description") String description)
    {
        if (request.getSession().getAttribute("user") == null) {
            throw new RedirectException("/auth/login");
        }

        if (StringUtils.isEmpty(debitAccountId) || StringUtils.isEmpty(creditAccountId) || StringUtils.isEmpty(transactionTypeId) || StringUtils.isEmpty(description) || StringUtils.isEmpty(amount)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (amount.contains(",")) {
            amount = amount.replace(",", "");
        }

        if (amount.contains("$")) {
            amount = amount.replace("$", "");
        }

        BigDecimal parsedAmount = new BigDecimal(amount);

        if (parsedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            accountingManager.createTransfer(debitAccountId, creditAccountId, transactionTypeId, transactionDate, parsedAmount, description);
        } catch (Exception ex) {
            return Response.ok(ex.getMessage()).status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok().build();
    }

    @GET
    @Path("/delete")
    public Response delete(@Context HttpServletRequest request, @QueryParam("transactionId") String transactionId)
    {
        if (request.getSession().getAttribute("user") == null) {
            throw new RedirectException("/auth/login");
        }

        if (StringUtils.isEmpty(transactionId)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            accountingManager.removeTransaction(transactionId);
        } catch (Exception ex) {
            return Response.ok(ex.getMessage()).status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok().build();
    }

}
