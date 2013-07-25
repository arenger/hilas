insert into analysis
select a.id, a.domain, h.source, a.sbsSum, null, null, null, null, b.numSites,
b.htmlBytes, ifnull(c.htmlLintCount,0), ifnull(c.htmlLintSum,0),
ifnull(d.jsBytes,0), ifnull(e.jsLintCount,0), ifnull(e.jsLintSum,0),
ifnull(f.cssBytes,0), ifnull(g.cssLintCount,0), ifnull(g.cssLintSum,0)
from ( -- sbs summary:
   select ad.id, ad.domain, sum(ifnull(sbr.result,0)) as sbsSum
   from domain ad join safebrowseresult sbr on ad.id = sbr.domainId
   where ifnull(sbr.result,0) >= 0 and ad.id = ?
   group by ad.id having sum(sbr.sbsId) = 15
) a join ( -- html stats:
   select bs.domainId, count(bs.id) as numSites,
   sum(bs.size) as htmlBytes from site bs
   where bs.domainId = ? and bs.visitState = 'VISITED' group by bs.domainId
) b on a.id = b.domainId left join ( -- html lint stats:
   select cs.domainId, count(cs.msgId) as htmlLintCount,
   sum(cs.severity) as htmlLintSum from (
      select cs2.domainId, hv.msgId, hlint.severity
      from site cs2 join htmlValid hv on cs2.id = hv.siteId
      join lintMsg hlint on hv.msgId = hlint.id
      where cs2.domainId = ? and cs2.visitState = 'VISITED'
      group by hv.msgId) cs group by cs.domainId
) c on a.id = c.domainId left join ( -- jsBytes:
   select ds.domainId, sum(ds.size) as jsBytes from (
      select ds2.domainId, js.size
      from site ds2 join sitejs sj on ds2.id = sj.siteid
      join javascript js on sj.jsid = js.id
      where ds2.domainid = ? group by js.id ) ds group by ds.domainId
) d on a.id = d.domainId left join ( -- js hint stats:
   select es.domainId, count(es.id) as jsLintCount,
   sum(es.severity) as jsLintSum from (
      select es2.domainId, lm.id, lm.severity
      from site es2 join sitejs sj on es2.id = sj.siteid
      join javascript js on sj.jsid = js.id
      join jshint jsh on js.id = jsh.jsid
      join lintMsg lm on jsh.msgId = lm.id
      where es2.domainid = ? group by lm.id ) es group by es.domainId
) e on a.id = e.domainId left join ( -- cssBytes:
   select fs.domainId, sum(fs.size) as cssBytes from (
      select s.domainId, css.size
      from site s join sitecss sc on s.id = sc.siteId
      join css on sc.cssId = css.id
      where s.domainId = ? group by css.id ) fs group by fs.domainId
) f on a.id = f.domainId left join ( -- css lint stats:
   select gs.domainId, count(gs.id) as cssLintCount,
   sum(gs.severity) as cssLintSum from (
      select gs2.domainId, lm.id, lm.severity
      from site gs2 join sitecss sc on gs2.id = sc.siteid
      join css on sc.cssId = css.id
      join cssvalid cssv on css.id = cssv.cssId
      join lintMsg lm on cssv.msgId = lm.id
      where gs2.domainid = ? group by lm.id ) gs group by gs.domainId
) g on a.id = g.domainId join (
   select hs.domainId, hs.source from site hs where hs.domainid = ?
   and hs.source not like 'hilas%' group by hs.domainId
) h on a.id = h.domainId;
-- NOTE the case when having "sum(sbr.sbsId) = 15" is not met
