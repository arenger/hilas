
alter table analysis add column wcat0 TINYINT;

update analysis set wcat0 = case
   when (wot0 >= 80) then 4
   when (wot0 >= 60) then 3
   when (wot0 >= 40) then 2
   when (wot0 >= 20) then 1
   when (wot0 >=  0) then 0
   else null
end;

select wcat0, count(domain),
   avg(htmlLintCount),  avg(cssLintCount),  avg(jsLintCount),
   avg(htmlBytes/1024), avg(cssBytes/1024), avg(jsBytes/1024),
   avg((htmlLintCount/htmlBytes)*10240) as hlPer10KiB,
   avg((cssLintCount/cssBytes)*10240)   as clPer10KiB,
   avg((jsLintCount/jsBytes)*10240)     as jlPer10KiB
from analysis
-- where source != 'black'
group by wcat0 order by wcat0;
