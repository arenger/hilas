
DROP TABLE IF EXISTS `hilas`.`analysis` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`analysis` (
  `domainId` VARCHAR(36) NOT NULL ,
  `domain` VARCHAR(256) NOT NULL ,

  `sbsSum` TINYINT NULL ,

-- `google` TINYINT(1) NULL ,
-- `mcafee` TINYINT(1) NULL ,
-- `norton` TINYINT(1) NULL ,
-- `wot`  TINYINT(1) NULL ,

  `wot0` TINYINT NULL ,
  `wot1` TINYINT NULL ,
  `wot2` TINYINT NULL ,
  `wot4` TINYINT NULL ,

  `numSites` INT NULL ,

  `htmlBytes`     INT      NULL ,
  `htmlLintCount` SMALLINT NULL ,
  `htmlLintSum`   SMALLINT NULL ,

  `jsBytes`     INT      NULL ,
  `jsLintCount` SMALLINT NULL ,
  `jsLintSum`   SMALLINT NULL ,

  `cssBytes`     INT      NULL ,
  `cssLintCount` SMALLINT NULL ,
  `cssLintSum`   SMALLINT NULL ,

  PRIMARY KEY (`domainId`)
) ENGINE = InnoDB;
