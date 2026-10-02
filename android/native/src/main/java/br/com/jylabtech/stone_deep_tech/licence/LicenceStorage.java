package br.com.jylabtech.stone_deep_tech.licence;

/** Armazenamento da licença validada (cache offline). */
public interface LicenceStorage {
    CheckLicenceEntity load();

    void save(CheckLicenceEntity entity);

    void delete();
}
