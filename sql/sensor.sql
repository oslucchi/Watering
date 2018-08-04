SELECT a.timestamp, a.value, b.value, c.value 
From History as a join History as b on 
		a.unityId = 0 and
		b.timestamp = a.timestamp and
		b.unityId = 1 and
		b.readValue > 0 
	join History as c on 
		c.timestamp = a.timestamp and
		c.unityId = 2 and
		c.readValue > 0
where a.timestamp > '2017-04-15 20:29:00' and
	a.readValue > 0 and
	a.type = 0 and
	b.type = 0 and
	c.type = 0 
order by timestamp;