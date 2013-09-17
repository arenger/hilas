create table localJs (id varchar(32) not null, PRIMARY KEY(id));

insert into localJs select sub.id as domCount from (
   select js.id from javascript js
   join sitejs sj on js.id = sj.jsid
   join site s on sj.siteid = s.id
   join domain d on s.domainid = d.id
   group by concat(js.id,d.main)
) sub group by sub.id having count(sub.id) = 1;

select count(*) as allJs   from javascript;
select count(*) as localJs from localJs;

delete from jshint     where jsid not in (select id from localJs);
delete from sitejs     where jsid not in (select id from localJs);
delete from javascript where id   not in (select id from localJs);

