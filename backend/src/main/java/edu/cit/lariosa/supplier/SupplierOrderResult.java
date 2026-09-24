package edu.cit.lariosa.supplier;

// Public result type returned by SupplierGateway
public class SupplierOrderResult {
    private final boolean success;
    private final String poNumber;
    private final int casesOrdered;
    private final int unitsOrdered; // cases * packSize
    private final String errorMessage;

    private SupplierOrderResult(boolean success, String poNumber, int casesOrdered, int unitsOrdered, String errorMessage) {
        this.success = success;
        this.poNumber = poNumber;
        this.casesOrdered = casesOrdered;
        this.unitsOrdered = unitsOrdered;
        this.errorMessage = errorMessage;
    }

    public static SupplierOrderResult success(String poNumber, int casesOrdered, int unitsOrdered) {
        return new SupplierOrderResult(true, poNumber, casesOrdered, unitsOrdered, null);
    }

    public static SupplierOrderResult failure(String errorMessage) {
        return new SupplierOrderResult(false, null, 0, 0, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public String getPoNumber() { return poNumber; }
    public int getCasesOrdered() { return casesOrdered; }
    public int getUnitsOrdered() { return unitsOrdered; }
    public String getErrorMessage() { return errorMessage; }
}
