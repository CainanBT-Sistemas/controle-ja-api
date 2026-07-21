package com.cainanbt.softwares.controleja.utils;

public class ConstsMessages {
    // --- TÍTULOS PADRÕES (Para usar no Front-end) ---
    public static final String ERROR_TITLE = "Erro";
    public static final String ACCESS_DENIED_TITLE = "Acesso negado";
    public static final String CRITICAL_ERROR_TITLE = "Falha crítica";
    public static final String OOPS_TITLE = "OOPS";
    public static final String REGISTRATION_ERROR_TITLE = "Erro de Cadastro";
    public static final String LIMIT_REACHED_TITLE = "Limite Atingido";
    public static final String CLOSED_TEST_TITLE = "Teste fechado";

    // --- AUTENTICAÇÃO E USUÁRIOS ---
    public static final String FAILURE_TO_FIND_USER = "Falha ao encontrar Usuário";
    public static final String ACCESS_DENIED = "Acesso negado. Você não tem permissão.";
    public static final String EMAIL_USER_NOT_FOUND = "Email de Usuário não encotrado";
    public static final String WRONG_LOGIN_CREDENTIALS = "Email ou senha incorretos. Tente novamente";
    public static final String BLOCKED_USER = "Sua conta está inativa ou bloqueada.";
    public static final String FALHA_ATUALIZAR_USUARIO = "Falha ao atualizar Usuário.";
    public static final String INVALID_TOKEN = "Sessão expirada. Faça login novamente.";
    public static final String INVALID_GOOGLE_TOKEN = "Não foi possível validar sua conta Google. Faça login novamente.";
    public static final String GOOGLE_LOGIN_NOT_CONFIGURED = "Login Google indisponível neste ambiente.";
    public static final String GOOGLE_EMAIL_CONFLICT = "Este email já está vinculado a outra forma de login.";
    public static final String EMAIL_IN_USE = "Este email já está em uso.";
    public static final String DATABASE_SAVE_ERROR = "Erro ao processar o cadastro no banco de dados.";
    public static final String SYSTEM_CRITICAL_ERROR = "Parece que houve um erro crítico no sistema, informe o desenvolvedor.";
    public static final String INVALID_CURRENT_PASSWORD = "Senha atual incorreta.";
    public static final String PASSWORD_REQUIREMENTS = "A senha deve ter no mínimo 6 caracteres.";
    public static final String MISSING_TOKEN_CLAIMS = "Refresh token não contém dados válidos de identificação.";
    public static final String INVALID_UUID_TOKEN = "ID no refresh token não é um formato válido.";
    public static final String CLOSED_TEST_ACCESS_DENIED = "Este aplicativo está em teste fechado. Este e-mail ainda não possui acesso.";

    // --- NÃO ENCONTRADO (404) ---
    public static final String ACCOUNT_NOT_FOUND = "Conta não encontrada ou já foi excluída.";
    public static final String CATEGORY_NOT_FOUND = "Categoria não encontrada ou já foi excluída.";
    public static final String CREDIT_CARD_NOT_FOUND = "Cartão de crédito não encontrado ou já foi excluído.";
    public static final String VEHICLE_NOT_FOUND = "Veículo não encontrado ou já foi excluído.";
    public static final String TRANSACTION_NOT_FOUND = "Transação não encontrada ou já foi excluída.";
    public static final String INVOICE_NOT_FOUND = "Invoice not found.";
    public static final String PARCEL_NOT_FOUND = "Parcela não encontrada.";
    public static final String RECURRENCE_RULE_NOT_FOUND = "Regra de recorrência não encontrada.";

    // --- PERMISSÕES (Regras de Negócio de Propriedade) ---
    public static final String NO_PERMISSION_ACCOUNT = "Conta inválida (Não pertence ao usuário).";
    public static final String NO_PERMISSION_VEHICLE = "Você não tem permissão para acessar ou alterar este veículo.";
    public static final String NO_PERMISSION_TRANSACTION = "Você não tem permissão para acessar ou alterar esta transação.";
    public static final String NO_PERMISSION_CARD = "Você não tem permissão para acessar ou alterar este cartão.";
    public static final String NO_PERMISSION_CATEGORY = "Você não tem permissão para acessar ou alterar esta categoria.";

    // --- CONTAS ---
    public static final String CANT_DELETE_MAIN_ACCOUNT = "Você não pode remover a conta principal.";
    public static final String CANT_UPDATE_ACCOUNT_NO_ID = "Impossível atualizar conta sem ID.";
    public static final String ACCOUNT_NAME_ALREADY_EXIST = "Este nome de conta já esta cadastrada";
    public static final String CANT_DELETE_ACCOUNT_WITH_LINKS = "Nao e possivel excluir esta conta porque existem lancamentos, saldo ou vinculos financeiros ativos. Resolva os vinculos antes de excluir.";


    // --- CARTÕES DE CRÉDITO ---
    public static final String LIMIT_REACHED_CARDS = "Usuários Free só podem ter 2 cartões de crédito. Assine o Premium!";
    public static final String CARD_ACCOUNT_NOT_FOUND = "Cartão não encontrado para esta conta.";
    public static final String CANT_DELETE_CARD_WITH_LINKS = "Nao e possivel excluir este cartao porque existem faturas, parcelas ou lancamentos vinculados. Quite, cancele ou ajuste os lancamentos antes de excluir.";

    // --- TRANSAÇÕES ---
    public static final String INVOICE_MISSING_TARGET = "Para pagar fatura, informe o ID da conta do cartão (targetAccountId).";
    public static final String TRANSFER_MISSING_TARGET = "Para Transferencia, informe o ID da conta de destino (targetAccountId).";
    public static final String TRANSFER_ACCOUNT_NOT_VALID = "Transferencia permitida apenas entre Carteira, Conta Bancaria e Poupanca. Cartao de credito e Investimento patrimonial nao podem ser usados como origem ou destino.";
    public static final String INVOICE_TARGET_NOT_CARD = "A conta de destino deve ser um Cartão de Crédito.";
    public static final String CARD_ONLY_EXPENSE = "Em conta de cartão, use apenas DESPESA.";
    public static final String INVOICE_PAYMENT_EDIT_BLOCKED = "Para corrigir pagamento de fatura, cancele o pagamento pela fatura e pague novamente.";

    // --- CATEGORIAS ---
    public static final String PARENT_CATEGORY_NOT_FOUND = "Categoria pai não encontrada.";

    // --- GENÉRICOS ---
    public static final String ENTITY_ALREADY_DELETED = "Este registro já foi excluído.";
    public static final String DELETE_SUCCESS = "Registro excluído com sucesso.";
    public static final String UPDATE_SUCCESS = "Registro atualizado com sucesso.";
    public static final String PASSWORD_CHANGED_SUCCESS = "Senha alterada com sucesso.";
    public static final String BALANCE_ADJUSTMENT_SUCCESS = "Reajuste de saldo concluido com sucesso";
}
