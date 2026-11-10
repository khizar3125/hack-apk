import re, json, sys

p = 'app/src/main/assets/games.json'
raw = open(p).read()

# Bare hex literals are not valid JSON. Convert them to decimal.
fixed = re.sub(r'0x([0-9a-fA-F]+)', lambda m: str(int(m.group(0), 16)), raw)
data = json.loads(fixed)
open(p, 'w').write(fixed)

print('games.json fixed, entries:', len(data))
print(json.dumps(data[0]['offsets'], indent=1))