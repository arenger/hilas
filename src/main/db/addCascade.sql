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
