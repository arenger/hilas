-- the current hilas.sql file should have all the needed delete cascade rules.
-- this file it for adding (some of) the rules to an existing (older) schema.

-- to delete from site:
ALTER TABLE SiteFrame DROP FOREIGN KEY fk_site_frame_site1;
ALTER TABLE SiteFrame ADD  CONSTRAINT fk_site_frame_site1
    FOREIGN KEY (`topsite` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE CASCADE
    ON UPDATE NO ACTION;

ALTER TABLE SiteFrame DROP FOREIGN KEY fk_site_frame_site2;
ALTER TABLE SiteFrame ADD  CONSTRAINT fk_site_frame_site2
    FOREIGN KEY (`topsite` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE CASCADE
    ON UPDATE NO ACTION;

ALTER TABLE siteJs DROP FOREIGN KEY fk_SiteJs_Site1;
ALTER TABLE siteJs ADD  CONSTRAINT fk_SiteJs_Site1
    FOREIGN KEY (`siteId` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE CASCADE
    ON UPDATE NO ACTION;

ALTER TABLE siteCss DROP FOREIGN KEY fk_SiteCss_Site1;
ALTER TABLE siteCss ADD  CONSTRAINT fk_SiteCss_Site1
    FOREIGN KEY (`siteId` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE CASCADE
    ON UPDATE NO ACTION;

ALTER TABLE HtmlValid DROP FOREIGN KEY fk_HtmlValid_Site1;
ALTER TABLE HtmlValid ADD  CONSTRAINT  fk_HtmlValid_Site1
    FOREIGN KEY (`siteId` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE CASCADE
    ON UPDATE NO ACTION;

-- to delete from domain:
ALTER TABLE SafeBrowseResult DROP FOREIGN KEY fk_SafeBrowseResult_Domain1;
ALTER TABLE SafeBrowseResult ADD  CONSTRAINT  fk_SafeBrowseResult_Domain1
    FOREIGN KEY (`domainId` )
    REFERENCES `hilas`.`Domain` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION;

ALTER TABLE site DROP FOREIGN KEY fk_Site_Domain1;
ALTER TABLE site ADD  CONSTRAINT  fk_Site_Domain1
    FOREIGN KEY (`domainId` )
    REFERENCES `hilas`.`Domain` (`id` )
    ON DELETE CASCADE
    ON UPDATE NO ACTION;

