package br.com.jylabtech.stone_deep_tech.licence;

/** Consulta da licença no servidor. Deve ser chamada fora da main thread. */
public interface LicenceRemote {
    LicenceResult checkLicence(String urlString, String licenceKey);
}
