package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;

public interface TransactionProcessor {
    boolean supports(TransactionDTO dto, Accounts account);

    Transactions process(TransactionDTO dto, Accounts account, Category category, Users user);
}
