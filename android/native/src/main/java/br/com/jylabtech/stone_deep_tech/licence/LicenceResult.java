package br.com.jylabtech.stone_deep_tech.licence;

public class LicenceResult {
    private String method;
    private String errorMessage;
    // Começa como erro: só vira sucesso (0) quando a licença for validada explicitamente.
    private int result = 999999;
    private String message;

    private CheckLicenceEntity licenceEntity;

    public void setLicenceEntity(CheckLicenceEntity entity) {
        this.licenceEntity = entity;
    }

    public CheckLicenceEntity getLicenceEntity() {
        return this.licenceEntity;
    }

    public void setMethod(String value) {
        this.method = value;
    }

    public void setResult(int value) {
        this.result = value;
    }

    public void setErrorMessage(String value) {
        this.errorMessage = value;
    }

    public void setMessage(String value) {
        this.message = value;
    }

    public String getMessage() {
        return this.message;
    }

    public String getMethod() {
        return this.method;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public int getResult() {
        return this.result;
    }

    public boolean isSuccess() {
        return this.result == 0;
    }
}

