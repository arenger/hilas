SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL,ALLOW_INVALID_DATES';

CREATE SCHEMA IF NOT EXISTS `hilas` DEFAULT CHARACTER SET utf8 ;
USE `hilas` ;

-- -----------------------------------------------------
-- Table `hilas`.`JavaScript`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`JavaScript` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`JavaScript` (
  `id` VARCHAR(36) NOT NULL ,
  `url` VARCHAR(512) NOT NULL COMMENT 'the url at which this js was first found by hilas' ,
  `md5` VARCHAR(32) NOT NULL COMMENT 'md5 hash of the js' ,
  `size` INT NOT NULL COMMENT 'in bytes' ,
  `jsHinted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'true if this js has been analyzed by the JsHint tool' ,
  PRIMARY KEY (`id`) ,
  UNIQUE INDEX `md5_UNIQUE` (`md5` ASC) )
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1;


-- -----------------------------------------------------
-- Table `hilas`.`SafeBrowseService`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`SafeBrowseService` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`SafeBrowseService` (
  `id` CHAR(1) NOT NULL ,
  `name` VARCHAR(32) NOT NULL ,
  PRIMARY KEY (`id`) )
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`Domain`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`Domain` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`Domain` (
  `id` VARCHAR(36) NOT NULL ,
  `domain` VARCHAR(256) NOT NULL ,
  PRIMARY KEY (`id`) ,
  UNIQUE INDEX `domain_UNIQUE` (`domain` ASC) )
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`SafeBrowseResult`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`SafeBrowseResult` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`SafeBrowseResult` (
  `domainId` VARCHAR(36) NOT NULL ,
  `sbsId` CHAR(1) NOT NULL COMMENT 'G,M,N,W, ...' ,
  `result` TINYINT(4) NULL COMMENT '0 is OK, 1 is warning, 2 is bad, null is unknown by this sbs' ,
  `extra` VARCHAR(128) NULL DEFAULT NULL COMMENT 'extra details provided by the sbs' ,
  `insTime` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ,
  PRIMARY KEY (`domainId`, `sbsId`) ,
  INDEX `fk_SafeBrowseResult_SafeBrowseService1_idx` (`sbsId` ASC) ,
  INDEX `fk_SafeBrowseResult_Domain1_idx` (`domainId` ASC) ,
  CONSTRAINT `fk_SafeBrowseResult_SafeBrowseService1`
    FOREIGN KEY (`sbsId` )
    REFERENCES `hilas`.`SafeBrowseService` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_SafeBrowseResult_Domain1`
    FOREIGN KEY (`domainId` )
    REFERENCES `hilas`.`Domain` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1;


-- -----------------------------------------------------
-- Table `hilas`.`Site`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`Site` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`Site` (
  `id` VARCHAR(36) NOT NULL ,
  `domainId` VARCHAR(36) NOT NULL ,
  `url` VARCHAR(512) NOT NULL ,
  `source` VARCHAR(32) NOT NULL COMMENT 'eg alexa, blacklist, crawler, subsite, etc' ,
  `visitTime` TIMESTAMP NULL COMMENT 'if/when this site was visited' ,
  `size` INT NULL COMMENT 'size in bytes of the initial html loaded.  no js-generated html will be included' ,
  `htmlValidated` TINYINT(1) NOT NULL DEFAULT 0 ,
  PRIMARY KEY (`id`) ,
  UNIQUE INDEX `url_UNIQUE` (`url` ASC) ,
  INDEX `fk_Site_Domain1_idx` (`domainId` ASC) ,
  CONSTRAINT `fk_Site_Domain1`
    FOREIGN KEY (`domainId` )
    REFERENCES `hilas`.`Domain` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1;


-- -----------------------------------------------------
-- Table `hilas`.`SiteJs`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`SiteJs` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`SiteJs` (
  `siteId` VARCHAR(36) NOT NULL ,
  `jsId` VARCHAR(36) NOT NULL ,
  PRIMARY KEY (`siteId`, `jsId`) ,
  INDEX `fk_SiteJs_JavaScript1_idx` (`jsId` ASC) ,
  INDEX `fk_SiteJs_Site1_idx` (`siteId` ASC) ,
  CONSTRAINT `fk_SiteJs_Site1`
    FOREIGN KEY (`siteId` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_SiteJs_JavaScript1`
    FOREIGN KEY (`jsId` )
    REFERENCES `hilas`.`JavaScript` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1;


-- -----------------------------------------------------
-- Table `hilas`.`SiteFrame`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`SiteFrame` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`SiteFrame` (
  `site` VARCHAR(36) NOT NULL ,
  `subsite` VARCHAR(36) NOT NULL ,
  PRIMARY KEY (`site`, `subsite`) ,
  INDEX `fk_site_frame_site1_idx` (`site` ASC) ,
  INDEX `fk_site_frame_site2_idx` (`subsite` ASC) ,
  CONSTRAINT `fk_site_frame_site1`
    FOREIGN KEY (`site` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_site_frame_site2`
    FOREIGN KEY (`subsite` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`JsHintMsg`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`JsHintMsg` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`JsHintMsg` (
  `id` VARCHAR(36) NOT NULL ,
  `message` VARCHAR(256) NOT NULL COMMENT 'the TEMPLATE of the error or warning' ,
  `severity` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'possibly helpful for minimizing set c' ,
  PRIMARY KEY (`id`) ,
  UNIQUE INDEX `message_UNIQUE` (`message` ASC) )
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`JsHint`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`JsHint` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`JsHint` (
  `jsId` VARCHAR(36) NOT NULL ,
  `msgId` VARCHAR(36) NOT NULL ,
  PRIMARY KEY (`jsId`, `msgId`) ,
  INDEX `fk_JsHint_JavaScript1_idx` (`jsId` ASC) ,
  INDEX `fk_JsHint_JsHintMsg1_idx` (`msgId` ASC) ,
  CONSTRAINT `fk_JsHint_JsHintMsg1`
    FOREIGN KEY (`msgId` )
    REFERENCES `hilas`.`JsHintMsg` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_JsHint_JavaScript1`
    FOREIGN KEY (`jsId` )
    REFERENCES `hilas`.`JavaScript` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`HtmlValidMsg`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`HtmlValidMsg` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`HtmlValidMsg` (
  `id` VARCHAR(36) NOT NULL ,
  `message` VARCHAR(256) NOT NULL COMMENT 'the TEMPLATE of the error or warning' ,
  PRIMARY KEY (`id`) )
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`HtmlValid`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`HtmlValid` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`HtmlValid` (
  `siteId` VARCHAR(36) NOT NULL ,
  `msgId` VARCHAR(36) NOT NULL ,
  PRIMARY KEY (`siteId`, `msgId`) ,
  INDEX `fk_HtmlValid_HtmlValidMsg1_idx` (`msgId` ASC) ,
  INDEX `fk_HtmlValid_Site1_idx` (`siteId` ASC) ,
  CONSTRAINT `fk_HtmlValid_HtmlValidMsg1`
    FOREIGN KEY (`msgId` )
    REFERENCES `hilas`.`HtmlValidMsg` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_HtmlValid_Site1`
    FOREIGN KEY (`siteId` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`CssValidMsg`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`CssValidMsg` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`CssValidMsg` (
  `id` VARCHAR(36) NOT NULL ,
  `message` VARCHAR(256) NOT NULL ,
  PRIMARY KEY (`id`) )
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`Css`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`Css` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`Css` (
  `id` VARCHAR(36) NOT NULL ,
  `url` VARCHAR(512) NOT NULL COMMENT 'the url at which this css was first found by hilas' ,
  `md5` VARCHAR(32) NOT NULL ,
  `size` INT NOT NULL COMMENT 'in bytes' ,
  `validated` TINYINT(1) NOT NULL DEFAULT 0 ,
  PRIMARY KEY (`id`) ,
  UNIQUE INDEX `md5_UNIQUE` (`md5` ASC) )
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`CssValid`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`CssValid` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`CssValid` (
  `cssId` VARCHAR(36) NOT NULL ,
  `msgId` VARCHAR(36) NOT NULL ,
  PRIMARY KEY (`cssId`, `msgId`) ,
  INDEX `fk_CssValid_CssValidMsg1_idx` (`msgId` ASC) ,
  INDEX `fk_CssValid_Css1_idx` (`cssId` ASC) ,
  CONSTRAINT `fk_CssValid_CssValidMsg1`
    FOREIGN KEY (`msgId` )
    REFERENCES `hilas`.`CssValidMsg` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_CssValid_Css1`
    FOREIGN KEY (`cssId` )
    REFERENCES `hilas`.`Css` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `hilas`.`SiteCss`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `hilas`.`SiteCss` ;

CREATE  TABLE IF NOT EXISTS `hilas`.`SiteCss` (
  `siteId` VARCHAR(36) NOT NULL ,
  `cssId` VARCHAR(36) NOT NULL ,
  PRIMARY KEY (`siteId`, `cssId`) ,
  INDEX `fk_SiteCss_Site1_idx` (`siteId` ASC) ,
  INDEX `fk_SiteCss_Css1_idx` (`cssId` ASC) ,
  CONSTRAINT `fk_SiteCss_Site1`
    FOREIGN KEY (`siteId` )
    REFERENCES `hilas`.`Site` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_SiteCss_Css1`
    FOREIGN KEY (`cssId` )
    REFERENCES `hilas`.`Css` (`id` )
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;

USE `hilas` ;


SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
