#!/usr/bin/env python3
"""Builds the Synthetic Communities benchmark: a large artificial DSMP dataset
whose ground truth is known by construction.

Every user follows exactly one of twelve fictional accounts. Each user's
tweets are drawn from a topic mixture: their community's own topic, the shared
interest topics their community belongs to, an occasional second community,
and real Twitter background vocabulary (words_100k.txt, at its observed
frequencies). Each community also has its own seasons, hours of the day,
weekdays and dated events, so text, activity and timing all carry signal.

The output is deterministic for a given seed: same parameters, same bytes.

    python3 Dataset/Benchmarks/build_synthetic_communities.py            # default size
    python3 Dataset/Benchmarks/build_synthetic_communities.py --users 600 --median-tweets 120

Writes, next to this script:
    <name>.txt                 the six-column DSMP dataset
    <name>-ground-truth.csv    every user's community and mixture weights
    <name>-manifest.json       parameters, counts, oracle accuracy, SHA-256
"""

import argparse
import datetime as dt
import hashlib
import json
import math
import os
import re
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

# ----------------------------------------------------------------------------- lexicons
# Twelve fictional accounts (no real organisation), each with its own topic.
COMMUNITIES = [
    ("AstroNightly", 21, """telescope nebula galaxy galaxies orbit orbital planet planets comet comets asteroid
        meteor meteors eclipse lunar solar moonrise starlight constellation constellations cosmos cosmic
        astronomer astronomers stargazing stargazer observatory spacecraft satellite satellites rocket
        launch launchpad astronaut astronauts mars jupiter saturn venus mercury neptune uranus pluto
        exoplanet exoplanets supernova pulsar quasar blackhole gravity spectroscopy redshift lightyear
        parsec equinox solstice aurora auroras milkyway andromeda orion perseids geminids leonids
        meteorite crater craters rover lander probe hubble webb infrared ultraviolet telescopes eyepiece
        refractor reflector dobsonian mount tripod astrophotography longexposure darksky skyglow zenith
        horizon twilight transit occultation conjunction opposition perihelion aphelion ecliptic
        celestial interstellar galactic nebulae cluster clusters starfield starfields spiral elliptical
        magnitude luminosity photon photons cosmology bigbang darkmatter darkenergy wormhole singularity
        eventhorizon spacewalk cosmonaut mission missions payload booster reentry splashdown capsule
        module iss spacestation moonwalk crescent gibbous waxing waning fullmoon newmoon harvestmoon
        bluemoon bloodmoon starparty skywatch skywatching binoculars finderscope planisphere starchart"""),
    ("TrailRunnersClub", 17, """trail trails ultra ultramarathon marathon halfmarathon tenk fivek pace
        splits tempo intervals fartlek hillrepeats longrun easyrun recoveryrun taper tapering mileage
        kilometers miles elevation vert switchbacks singletrack summit ridge ridgeline descent downhill
        uphill scramble rocky rooty muddy trailhead aidstation crew pacer pacers bib bibs finisher
        finishline startline chip podium agegroup personalbest personalrecord cadence stride footstrike
        gaiters trailshoes lugs grip hydrationvest flask flasks gels gel electrolytes saltpills
        blisters chafing anklesprain plantar shinsplints itband foamroller stretching mobility
        strengthwork lunges squats plank coreworkout heartrate zone zones vo threshold lactate
        aerobic anaerobic fueling bonk bonking wall dnf dns cutoff cutoffs headlamp nightrun
        sunriserun dawnpatrol trailcrew runclub groupruns parkrun skyrunning fellrunning orienteering
        compass gpswatch strava segment segments kudos garmin courserecord fastestknowntime fkt
        backyardultra loop loops mountainrace vertical skyrace trailrace racereport racekit
        dropbag dropbags sweep sweeper volunteer volunteers trailangel switchback cairn cairns
        stream crossing creekcrossing waterfallrun forestrun canyonrun desertrun snowrun"""),
    ("BakeHouseDaily", 13, """sourdough starter levain loaf loaves crumb crust boule batard baguette
        focaccia ciabatta brioche challah croissant croissants laminated lamination puffpastry
        danish pastry pastries scones scone muffin muffins cupcake cupcakes frosting buttercream
        ganache meringue macarons macaron eclair eclairs choux profiteroles tart tarts galette
        pie pies piecrust shortbread biscotti cookie cookies brownie brownies blondies cheesecake
        layercake bundt chiffon genoise sponge rolledcake swissroll fondant marzipan pralines
        caramel toffee custard creme brulee flan pudding cinnamonroll cinnamonrolls sticky buns
        knead kneading proofing proof rise prove bulk ferment fermentation autolyse hydration
        bannetons banneton dutchoven scoring lame bench scraper whisk spatula rollingpin
        stand mixer dough doughs flour wholewheat rye spelt semolina yeast instantyeast
        bakingsoda bakingpowder butter unsalted vanilla vanillabean cocoa chocolatechips
        sprinkles glaze icing zest lemoncurd jam preserves berries blueberries raspberry
        rhubarb apricot pecan pecans walnuts almonds hazelnut pistachio oven preheat crumble
        streusel bakeoff showstopper softbake bakery baker bakers patisserie viennoiserie"""),
    ("CodeCraftDevs", 15, """code coding programmer programmers developer developers compiler compilers
        debugger debugging bug bugs bugfix refactor refactoring repository repo commit commits
        merge rebase branch branches pullrequest codereview linter linting unittest unittests
        testing testsuite integration deployment deploy pipeline pipelines container containers
        kubernetes docker microservice microservices backend frontend fullstack api apis endpoint
        endpoints json yaml schema database databases query queries index indexes sql nosql
        postgres redis kafka queue queues cache caching latency throughput concurrency threads
        mutex deadlock async await promise promises callback callbacks closure closures
        recursion algorithm algorithms datastructure hashmap linkedlist binarytree graph graphs
        typescript javascript python rust golang java kotlin swift haskell scala clojure elixir
        functional immutable typesafety generics interface interfaces inheritance polymorphism
        monorepo opensource maintainer maintainers changelog semver release releases hotfix
        rollback observability logging metrics tracing profiler profiling benchmark benchmarks
        memoryleak garbagecollector runtime bytecode assembly webassembly terminal shell bash
        vim emacs editor ide keyboardshortcuts dotfiles pairprogramming hackathon sprint
        standup kanban backlog techdebt legacycode stacktrace segfault nullpointer"""),
    ("GreenThumbGarden", 12, """garden gardens gardening gardener gardeners seedlings seedling seeds
        sowing germination transplant transplanting compost composting mulch mulching soil
        topsoil loam clay raisedbed raisedbeds greenhouse polytunnel coldframe cloche trellis
        perennials perennial annuals biennial bulbs tulips daffodils dahlias peonies roses
        lavender rosemary thyme basil mint parsley chives oregano sage herbs herbgarden
        tomatoes tomato zucchini courgette cucumbers pumpkins squash beans peas lettuce
        spinach kale chard radishes carrots beetroot onions garlic potatoes leeks brassicas
        pollinators bees beekeeping hive honeybees butterflies wildflowers meadow hedgerow
        pruning prune deadheading weeding weeds hoe trowel secateurs rake wheelbarrow
        wateringcan irrigation dripline rainbarrel frost frostdate hardiness overwinter
        foliage blooms blossom blossoms orchard fruittrees apples pears cherries plums
        strawberries raspberries gooseberries currants vines grapevine ivy hostas ferns
        succulents cacti houseplants monstera pothos repotting rootbound cuttings propagation
        aphids slugs snails blight mildew companionplanting cropsrotation allotment veggiepatch"""),
    ("JazzCellarLive", 9, """jazz bebop swing bigband quartet quintet trio combo standards
        improvisation improvise improvised solo solos saxophone sax tenorsax altosax trumpet
        trombone clarinet flugelhorn upright doublebass contrabass drums drummer brushes
        cymbals hihat snare pianist piano keys organ hammond vibraphone guitarist archtop
        scat vocalist crooner ballad ballads bossanova samba latinjazz fusion smoothjazz
        hardbop cooljazz modaljazz freejazz avantgarde ragtime dixieland stride boogie blues
        bluesy groove grooves syncopation polyrhythm walkingbass comping voicings chords
        changes turnaround bridge chorus vamp riff riffs lick licks setlist encore gig
        gigs jamsession jamnight openmic lounge cellar speakeasy nightclub soundcheck
        tuning reed reeds mouthpiece valve valves mute embouchure sightreading leadsheet
        realbook transcription vinyl record records lp albums liner linernotes bluenote
        sideman bandleader headliner residency matinee latenight setbreak lastcall"""),
    ("PawsRescueNet", 8, """rescue rescued adoption adopt adopted adopter foster fostering
        shelter shelters kennel kennels puppy puppies kitten kittens dog dogs cat cats
        senior seniordog strays stray microchip neuter spay vaccinations vet vets
        veterinarian checkup leash harness collar crate cratetraining housetrained
        pottytraining treats kibble wetfood chewtoy squeaky fetch tugtoy dogpark walkies
        cuddles snuggles purring purr meow woof bark barking zoomies tailwags belly
        rubs grooming bath brushing shedding fur whiskers paws pawprint litter
        litterbox scratching scratchingpost catnip lapcat furbaby forever
        foreverhome fundraiser donations donate volunteerday transport intake surrender
        rehome rehoming bonded pair siblings tripod blind deaf specialneeds medicalfund
        recovery surgery hospice sanctuary farmrescue rabbits guinea ferrets parrots
        tortoise foster failure fosterfail goodboy goodgirl heartworm fleas ticks"""),
    ("MarketPulseFin", 10, """stocks stock equities equity bonds bond yields yield treasury
        treasuries inflation deflation recession earnings revenue guidance dividend
        dividends buyback valuation multiples priceearnings bullmarket bearmarket rally
        selloff correction volatility vix futures options calls puts hedge hedging
        portfolio allocation diversification rebalance indexfund etf etfs mutualfund
        brokerage trader traders trading daytrading swingtrading shortselling margin
        leverage liquidity bidask spread orderbook ticker tickers nasdaq benchmark
        centralbank ratehike ratecut basispoints monetary fiscal stimulus gdp payrolls
        unemployment cpi ppi consumer spending housingstarts mortgage mortgages credit
        creditspread default defaults junk investmentgrade commodities crude oilprice
        gold silver copper currency currencies forex dollar euro yen exchange rate
        quarterly annualreport tenk filings ipo ipos merger acquisition spinoff
        analyst analysts upgrade downgrade pricetarget consensus outlook forecast"""),
    ("PixelArcadeHQ", 11, """gaming gamer gamers videogame videogames console consoles controller
        joystick arcade arcades retro retrogaming speedrun speedrunner speedruns glitch
        glitches patch patches update dlc expansion multiplayer coop singleplayer
        roguelike roguelite platformer metroidvania rpg jrpg mmorpg shooter fps battle
        royale lobby matchmaking ranked leaderboard achievement achievements trophy
        trophies unlock unlocked boss bosses bossfight checkpoint savefile respawn
        loot lootbox inventory crafting skilltree levelup grind grinding quest quests
        sidequest openworld sandbox indiegame indiedev pixelart sprite sprites
        soundtrack chiptune cutscene cutscenes framerate fps resolution graphics
        shader shaders raytracing headset vr esports tournament stream streamer
        streaming twitchstream clips highlights modding mods modder emulator cartridge
        handheld preorder launchday gameplay walkthrough playthrough newgameplus"""),
    ("SaltwaterSurfCo", 7, """surf surfing surfer surfers wave waves swell swells breakers
        reef reefbreak beachbreak pointbreak barrel barrels tube offshore onshore
        glassy choppy lineup paddle paddling duckdive popup takeoff wipeout wipeouts
        longboard shortboard fish board boards wax leash wetsuit booties rashguard
        fins quiver shaper shaping surfboard surfboards tide tides lowtide hightide
        swellforecast buoy buoys groundswell windswell setwaves sandbar sandbars
        dawnpatrolsurf sunsetsession session sessions noseride hangten cutback
        bottomturn aerial floater snap carve carving shoreline coastline cove
        lagoon dunes seaspray saltwater seaweed dolphins pelicans seal seals
        lifeguard ripcurrent riptide boardshorts surfcamp surftrip surftown"""),
    ("BookNookReads", 9, """book books reading reader readers novel novels novella fiction
        nonfiction memoir biography poetry poems poet anthology chapter chapters
        prologue epilogue protagonist antagonist plot plots plottwist character
        characters narrator worldbuilding fantasy scifi mystery thriller whodunit
        romance historicalfiction literaryfiction debut debutnovel author authors
        signing booksigning bookclub bookstore bookshop library libraries librarian
        hardcover paperback ebook audiobook audiobooks kindle bookmark bookmarks
        tbr tbrpile bookshelf bookshelves annotate annotating highlights quotes
        review reviews rating bestseller bestsellers longlist shortlist prize
        publisher publishing manuscript draft editor editing proofs galley arc
        sequel series trilogy standalone prequel spinoff fanfiction readathon
        cozyread pageturner slowburn unputdownable bookworm bibliophile"""),
    ("CityCycleHub", 10, """bike bikes bicycle bicycles cycling cyclist cyclists commute
        commuting commuter bikelane bikelanes protectedlane cycletrack bikeshare
        ebike ebikes cargobike foldingbike fixie roadbike gravelbike helmet helmets
        lights reflector reflectors pannier panniers basket lock ulock chainlock
        puncture flat flattire tubeless innertube pump chain chainring cassette
        derailleur gears shifting brakes discbrakes rimbrakes saddle handlebars
        pedals cleats clipless spokes wheels tires tyres bikeshop mechanic tuneup
        crosswalk intersection junction roundabout trafficcalming streetscape
        urbanism walkable carfree pedestrian pedestrians sidewalk curb parking
        bikeparking bikerack transit tram bus subway ride rides groupride
        criticalmass bikebus schoolrun citycouncil infrastructure greenway
        towpath cityride nightride bikeweek biketowork bikecommute"""),
]

# Shared interest topics: each links several communities, so communities are
# related to different degrees and the task is not trivially separable.
SHARED = [
    ("outdoors", ["TrailRunnersClub", "SaltwaterSurfCo", "CityCycleHub", "GreenThumbGarden", "AstroNightly"],
     """weather forecast sunshine sunny rain rainy windy breeze sunrise sunset morning outdoors
        outside nature fresh air mountains mountain coast beach park parks lake river valley
        views view scenic hike hiking camping campfire tent backpack adventure explore exploring
        weekend getaway roadtrip wildlife birds birdsong clouds cloudy storm thunder heatwave
        spring summer autumn winter seasons fog misty golden hour"""),
    ("fitness", ["TrailRunnersClub", "CityCycleHub", "SaltwaterSurfCo"],
     """training workout workouts fitness endurance stamina strength recovery restday rest
        stretch hydration hydrate protein nutrition calories sleep goals goal progress
        consistency motivation discipline coach coaching plan plans session sweat sore
        muscles injury physio wellness healthy health energy"""),
    ("tech", ["CodeCraftDevs", "PixelArcadeHQ", "MarketPulseFin"],
     """tech technology startup startups app apps software hardware device devices gadget
        gadgets laptop laptops smartphone screen screens chip chips processor cloud server
        servers data dataset ai machinelearning automation digital online platform platforms
        product launch beta feature features users subscription"""),
    ("culture", ["BookNookReads", "JazzCellarLive", "AstroNightly"],
     """festival festivals museum museums gallery exhibit exhibition podcast podcasts
        documentary film films cinema theatre theater concert concerts lecture talk talks
        culture history art artist artists interview interviews tickets program evening
        series collection classic classics"""),
    ("homelife", ["BakeHouseDaily", "GreenThumbGarden", "PawsRescueNet", "BookNookReads"],
     """home kitchen coffee tea breakfast brunch dinner recipe recipes cozy family kids
        weekend sunday morning afternoon neighbours neighbors friends holiday holidays
        cleaning chores porch backyard window windowsill candles blanket comfort homemade
        handmade gift gifts"""),
]

FIRST_NAMES = """Aaron Abigail Adam Adrian Aisha Alan Albert Alex Alice Alicia Amanda Amber Amelia Amy Andrea Andrew
    Angela Anna Anthony Antonio Ariana Arthur Ashley Audrey Austin Barbara Beatrice Ben Benjamin Bianca Blake
    Brandon Brenda Brian Brianna Brittany Bruce Bryan Caleb Cameron Carla Carlos Carmen Caroline Carter Cassandra
    Catherine Cecilia Charles Charlotte Chelsea Chloe Chris Christian Christina Claire Clara Cody Colin Connor
    Craig Crystal Cynthia Daisy Dale Damian Daniel Danielle Darius David Dawn Dean Deborah Derek Diana Diego
    Dominic Donna Dylan Edward Elena Eli Elijah Elizabeth Ella Ellen Emily Emma Eric Erica Erin Ethan Eva Evan
    Felix Fiona Frances Frank Gabriel Gavin Gemma George Georgia Gloria Grace Grant Hailey Hannah Harold Harper
    Harry Hassan Hazel Heather Hector Helen Henry Holly Hugo Ian Ingrid Isaac Isabel Ivan Jack Jacob Jade James
    Jamie Jane Jasmine Jason Javier Jenna Jennifer Jeremy Jessica Joanna Joel John Jonah Jordan Jose Joseph
    Joshua Joy Julia Julian Justin Karen Karl Kate Katherine Kayla Keith Kelly Kevin Kim Kyle Laura Lauren
    Leah Leo Leon Liam Lily Linda Lisa Logan Lorenzo Louis Lucas Lucy Luis Luke Lydia Madeline Maggie Malik
    Marcus Margaret Maria Marie Mark Martin Mary Mason Matthew Maya Megan Melissa Mia Michael Michelle Miguel
    Mila Miles Molly Monica Nadia Naomi Natalie Nathan Neil Nicholas Nicole Nina Noah Nolan Nora Oliver Olivia
    Omar Oscar Owen Paige Pamela Patrick Paul Paula Peter Philip Priya Quinn Rachel Rafael Ramon Rebecca Riley
    Robert Robin Rosa Ruby Ryan Sabrina Samantha Samuel Sandra Sara Sarah Scott Sean Sebastian Selena Seth
    Sharon Sienna Simon Sofia Sophie Stella Stephen Steven Susan Sylvia Tanya Taylor Teresa Theo Thomas Tiffany
    Timothy Tina Tobias Tracy Trevor Tyler Valerie Vanessa Victor Victoria Vincent Violet Walter Wendy William
    Xavier Yasmin Yusuf Zachary Zara Zoe""".split()

LAST_NAMES = """Abbott Acosta Adams Aguilar Ahmed Alexander Ali Allen Alvarez Anderson Andrews Armstrong Arnold Atkins
    Bailey Baker Baldwin Banks Barnes Barrett Barton Bates Bauer Beck Bell Bennett Berg Berry Bishop Black Blair
    Booth Bowen Bowman Boyd Bradley Brady Brennan Brooks Brown Bryant Burke Burns Burton Bush Butler Byrne
    Caldwell Campbell Cannon Carlson Carpenter Carr Carroll Carter Castillo Castro Chambers Chan Chang Chapman
    Chavez Chen Clark Clarke Cohen Cole Coleman Collins Cooper Cortez Cox Craig Crawford Cruz Cunningham Curtis
    Dalton Daniels Davidson Davies Davis Dawson Day Dean Delgado Diaz Dixon Doyle Duncan Dunn Edwards Elliott
    Ellis Evans Farmer Ferguson Fernandez Fields Fischer Fisher Fleming Fletcher Flores Ford Foster Fowler Fox
    Francis Franklin Freeman Fuller Gallagher Garcia Gardner Garrett Gibson Gilbert Gill Gomez Gonzales Gordon
    Graham Grant Gray Green Gregory Griffin Gutierrez Hale Hall Hamilton Hansen Hardy Harper Harris Harrison
    Hart Harvey Hawkins Hayes Henderson Henry Herrera Hicks Hill Hoffman Holland Holmes Hopkins Howard Howell
    Hudson Hughes Hunt Hunter Jackson James Jenkins Jensen Johnson Johnston Jones Jordan Kaur Keller Kelly
    Kennedy Khan Kim King Klein Knight Kramer Lambert Lane Larson Lawrence Lawson Lee Lewis Lin Little Liu
    Lloyd Long Lopez Lowe Lucas Lynch Mack Malone Mann Marsh Marshall Martin Martinez Mason Matthews Maxwell
    May McCarthy McDonald Medina Mendoza Meyer Miller Mills Mitchell Montgomery Moore Morales Moreno Morgan
    Morris Morrison Murphy Murray Myers Nash Nelson Newman Nguyen Nichols Noble Norris Obrien Oconnor Oliver
    Olson Ortiz Owens Palmer Park Parker Patel Patterson Payne Pearson Perez Perkins Perry Peters Phillips
    Pierce Porter Powell Price Quinn Ramirez Ramos Reed Reid Reyes Reynolds Rhodes Rice Richards Richardson
    Riley Rivera Roberts Robinson Rodriguez Rogers Romero Rose Ross Russell Ryan Salazar Sanchez Sanders
    Santos Saunders Schmidt Scott Shah Shaw Silva Simmons Simpson Singh Smith Snyder Soto Spencer Stanley
    Stevens Stewart Stone Sullivan Sutton Swanson Taylor Thomas Thompson Torres Tran Tucker Turner Vargas
    Vasquez Wagner Walker Wallace Walsh Walters Wang Ward Warren Washington Watson Weaver Webb Wells West
    Wheeler White Williams Willis Wilson Wong Wood Woods Wright Wu Yang Young Zhang""".split()

LINK_PREFIX = re.compile(r"^(http|www|bitly)")


def stop_words():
    """The simulator's own stop-word list, read from ControllerAgent.java."""
    source = open(os.path.join(ROOT, "TwitterGatherDataFollowers", "userRyersonU", "ControllerAgent.java"),
                  encoding="utf-8").read()
    start = source.index("stopWordsArray")
    block = source[start:source.index("};", start)]
    return set(re.findall(r'"([^"]+)"', block))


def usable(word, stops):
    return (word.isascii() and word.isalpha() and word.islower() and 3 <= len(word) <= 24
            and word not in stops and not LINK_PREFIX.match(word))


def build_lexicons(stops):
    seen = set()
    topics = []  # (name, words)
    for name, _, text in COMMUNITIES:
        words = []
        for w in text.split():
            if usable(w, stops) and w not in seen:
                seen.add(w)
                words.append(w)
        topics.append((name, words))
    shared = []
    for name, members, text in SHARED:
        words = []
        for w in text.split():
            if usable(w, stops) and w not in seen:
                seen.add(w)
                words.append(w)
        shared.append((name, members, words))
    background = []
    weights = []
    for line in open(os.path.join(ROOT, "words_100k.txt"), encoding="utf-8"):
        parts = line.strip().split("\t")
        if len(parts) != 2:
            continue
        w = parts[0].strip().lower()
        if usable(w, stops) and w not in seen and len(w) <= 14:
            seen.add(w)
            background.append(w)
            weights.append(float(parts[1]))
    return topics, shared, background, np.array(weights)


def zipf(n, exponent, rng):
    """A Zipf-like distribution over n words in a random order."""
    ranks = np.arange(1, n + 1, dtype=float)
    p = (ranks + 1.0) ** -exponent
    p /= p.sum()
    return p[rng.permutation(n)]


def similarity_check(user_counts, community_of, users, vocab_size, k):
    """Similarity's own scoring rule, recomputed independently: TF-IDF weights
    tf * log2(N / df), L2-normalised, cosine; every user is a target once, the
    ten highest-scoring other users are its list (ties by name), and a user is
    relevant when it follows the same account. Returns P@10, top-1 accuracy and
    the exact chance level of P@10."""
    n = len(users)
    df = np.zeros(vocab_size)
    for u in range(n):
        df[user_counts[u][0]] += 1
    idf = np.log2(n / np.maximum(df, 1))
    w = np.zeros((n, vocab_size), dtype=np.float32)
    for u in range(n):
        idx, cnt = user_counts[u]
        w[u, idx] = cnt * idf[idx]
    w /= np.maximum(np.linalg.norm(w, axis=1, keepdims=True), 1e-30)
    scores = w @ w.T
    np.fill_diagonal(scores, -np.inf)
    name_rank = np.argsort(np.argsort(np.array(users)))
    precision = []
    top1 = []
    sizes = np.bincount(community_of, minlength=k)
    chance = []
    for u in range(n):
        order = np.lexsort((name_rank, -scores[u]))[:10]
        same = community_of[order] == community_of[u]
        precision.append(same.mean())
        top1.append(bool(same[0]))
        chance.append((sizes[community_of[u]] - 1) / (n - 1))
    return {"precision_at_10": round(float(np.mean(precision)), 4), "top1": round(float(np.mean(top1)), 4),
            "chance_precision_at_10": round(float(np.mean(chance)), 4)}


# ----------------------------------------------------------------------------- generation
def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--users", type=int, default=1500)
    parser.add_argument("--median-tweets", type=float, default=5500.0)
    parser.add_argument("--seed", type=int, default=20261002)
    parser.add_argument("--name", default=None)
    parser.add_argument("--start", default="2019-01-01")
    parser.add_argument("--months", type=int, default=72)
    parser.add_argument("--own-scale", type=float, default=0.04)
    parser.add_argument("--season", type=float, default=0.5, help="strength of each community's seasonal peak")
    parser.add_argument("--rhythm", type=float, default=0.35, help="share of a user's daily and weekly rhythm set by the community")
    parser.add_argument("--event", type=float, default=0.30, help="share of own-topic words naming the event in an event month")
    parser.add_argument("--own-floor", type=float, default=0.008)
    parser.add_argument("--silent", type=float, default=0.15)
    parser.add_argument("--cross", type=float, default=0.75)
    parser.add_argument("--cross-max", type=float, default=0.10)
    args = parser.parse_args()

    rng = np.random.default_rng(args.seed)
    stops = stop_words()
    topics, shared, background, background_freq = build_lexicons(stops)
    k = len(topics)
    names = [t[0] for t in topics]
    size_weights = np.array([c[1] for c in COMMUNITIES], dtype=float)
    size_weights /= size_weights.sum()

    # Word distributions: one per community topic, one per shared topic, and background.
    topic_p = [zipf(len(words), 0.95, rng) for _, words in topics]
    shared_p = [zipf(len(words), 0.85, rng) for _, _, words in shared]
    bg_p = background_freq ** 0.8
    bg_p /= bg_p.sum()

    # Vocabulary index: community topics, then shared topics, then background.
    vocab = []
    offsets = []
    for _, words in topics:
        offsets.append(len(vocab))
        vocab.extend(words)
    shared_offsets = []
    for _, _, words in shared:
        shared_offsets.append(len(vocab))
        vocab.extend(words)
    bg_offset = len(vocab)
    vocab.extend(background)
    vocab = np.array(vocab, dtype=object)
    membership = {name: [i for i, (_, members, _) in enumerate(shared) if name in members] for name in names}

    # Each community's timing: seasonal peak month, active hours, weekday bias, dated events.
    months = args.months
    start = dt.datetime.strptime(args.start, "%Y-%m-%d")
    month_starts = []
    for m in range(months + 1):
        year = start.year + (start.month - 1 + m) // 12
        month = (start.month - 1 + m) % 12 + 1
        month_starts.append(dt.datetime(year, month, 1))
    hour_peaks = {"AstroNightly": [22, 2], "TrailRunnersClub": [6, 18], "BakeHouseDaily": [6, 10],
                  "CodeCraftDevs": [14, 23], "GreenThumbGarden": [8, 17], "JazzCellarLive": [21, 23],
                  "PawsRescueNet": [12, 19], "MarketPulseFin": [9, 15], "PixelArcadeHQ": [20, 0],
                  "SaltwaterSurfCo": [7, 16], "BookNookReads": [20, 13], "CityCycleHub": [8, 17]}
    weekday_bias = {"MarketPulseFin": [1.4, 1.4, 1.4, 1.4, 1.3, 0.3, 0.2], "SaltwaterSurfCo": [0.8, 0.7, 0.7, 0.8, 1.0, 1.6, 1.6],
                    "TrailRunnersClub": [0.8, 0.8, 0.9, 0.8, 0.8, 1.5, 1.6], "CodeCraftDevs": [1.2, 1.3, 1.3, 1.2, 1.1, 0.5, 0.4],
                    "JazzCellarLive": [0.6, 0.6, 0.8, 1.0, 1.5, 1.7, 0.8]}
    community_time = []
    for c, name in enumerate(names):
        peak = rng.integers(0, 12)
        season = np.array([math.exp(args.season * math.cos(2 * math.pi * ((month_starts[m].month - 1) - peak) / 12.0))
                           for m in range(months)])
        hours = np.full(24, 0.15)
        for h in hour_peaks[name]:
            for d in range(-3, 4):
                hours[(h + d) % 24] += math.exp(-d * d / 3.0)
        hours /= hours.sum()
        days = np.array(weekday_bias.get(name, [1.0] * 7), dtype=float)
        days /= days.sum()
        events = []
        tail = list(range(len(topics[c][1])))[40:]
        for _ in range(10):
            month = int(rng.integers(0, months))
            words = rng.choice(tail, size=6, replace=False)
            events.append((month, offsets[c] + words))
        community_time.append((season, hours, days, events))

    # Users: community, unique name, id, topic mixture, activity window and volume.
    n = args.users
    community_of = rng.choice(k, size=n, p=size_weights)
    used = set()
    users = []
    for i in range(n):
        while True:
            name = rng.choice(FIRST_NAMES) + rng.choice(LAST_NAMES)
            if name not in used:
                used.add(name)
                break
        users.append(name)
    user_ids = rng.choice(np.arange(100000, 99999999), size=n, replace=False)
    own = rng.beta(2.0, 5.0, size=n) * args.own_scale + args.own_floor   # own community topic weight
    silent = rng.random(n) < args.silent                      # follows the account but rarely tweets its topic
    own = np.where(silent, rng.uniform(0.0, args.own_floor, size=n), own)
    shared_w = rng.uniform(0.04, 0.12, size=n)                # weight on the community's shared interests
    cross = (rng.random(n) < args.cross) | silent             # a second community's topic
    cross_c = (community_of + rng.integers(1, k, size=n)) % k
    cross_w = np.where(cross, rng.uniform(0.01, args.cross_max, size=n), 0.0)
    volume = np.clip(np.round(rng.lognormal(math.log(args.median_tweets), 0.6, size=n)), 150, 9000).astype(int)
    span = np.clip(np.round(rng.lognormal(math.log(30), 0.6, size=n)), 6, months).astype(int)
    first = np.array([rng.integers(0, months - s + 1) for s in span])

    name = args.name or "Synthetic-Communities-{}-Users".format(n)
    out_path = os.path.join(HERE, name + ".txt")
    truth_path = os.path.join(HERE, name + "-ground-truth.csv")
    rows_written = 0  # below 2**24, so tweet ids are unique by construction
    tokens_written = 0
    community_counts = np.zeros(k, dtype=int)
    tweet_counts = np.zeros(k, dtype=int)
    oracle_counts = np.zeros((n, k))
    user_counts = {}
    oracle_logp = []
    # Mean word distribution of each community (for the oracle that knows the generator).
    mean_own = float(own.mean())
    mean_shared = float(shared_w.mean())
    mean_cross = float(cross_w.mean())
    for c in range(k):
        p = np.full(len(vocab), 1e-12)
        p[offsets[c]:offsets[c] + len(topic_p[c])] += mean_own * topic_p[c]
        mem = membership[names[c]]
        for s in mem:
            p[shared_offsets[s]:shared_offsets[s] + len(shared_p[s])] += mean_shared / len(mem) * shared_p[s]
        for other in range(k):
            if other != c:
                p[offsets[other]:offsets[other] + len(topic_p[other])] += mean_cross / (k - 1) * topic_p[other]
        rest = 1.0 - mean_own - (mean_shared if mem else 0.0) - mean_cross
        p[bg_offset:] += rest * bg_p
        oracle_logp.append(np.log(p / p.sum()))
    oracle_logp = np.array(oracle_logp)

    # Calendar tables: each month's first day (as a day number since 1970-01-01, a Thursday) and length.
    epoch = np.datetime64("1970-01-01")
    month_day0 = np.array([(np.datetime64(ms.strftime("%Y-%m-%d")) - epoch).astype(int) for ms in month_starts])
    month_len = np.diff(month_day0)
    # Per community: the event words of each month (-1: no event that month).
    event_tables = []
    for c in range(k):
        season, hours, days, events = community_time[c]
        ev = np.full((months, 6), -1, dtype=np.int64)
        for month, words in events:
            if ev[month, 0] < 0:
                ev[month] = words
        event_tables.append(ev)

    term_user_counts = np.zeros(len(vocab), dtype=np.int64)
    order = sorted(range(n), key=lambda u: (names[community_of[u]], users[u]))
    base_id = 1100000000000000000
    tweet_epoch_minutes = int((np.datetime64("2010-11-04") - epoch).astype(int)) * 1440
    with open(out_path, "w", encoding="utf-8", newline="\n") as out, \
            open(truth_path, "w", encoding="utf-8", newline="\n") as truth:
        truth.write("user,user_id,followee,own_topic_weight,shared_weight,second_interest,second_weight,tweets\n")
        for u in order:
            c = community_of[u]
            season, hours, days, events = community_time[c]
            # Topic mixture: own topic, shared interests, an optional second community, background.
            parts = [("topic", c, own[u])]
            mem = membership[names[c]]
            for s_index in mem:
                parts.append(("shared", s_index, shared_w[u] / len(mem)))
            if cross[u]:
                parts.append(("topic", cross_c[u], cross_w[u]))
            used_w = sum(p[2] for p in parts)
            parts.append(("bg", 0, max(0.05, 1.0 - used_w)))
            weights = np.array([p[2] for p in parts])
            weights /= weights.sum()
            m0, m1 = first[u], first[u] + span[u]
            month_w = season[m0:m1].copy()
            month_w /= month_w.sum()
            count = int(volume[u])
            tweet_months = m0 + rng.choice(m1 - m0, size=count, p=month_w)
            lengths = 4 + rng.poisson(5.5, size=count)
            total = int(lengths.sum())
            which = rng.choice(len(parts), size=total, p=weights)
            words = np.empty(total, dtype=np.int64)
            for j, (kind, idx, _) in enumerate(parts):
                mask = which == j
                m = int(mask.sum())
                if m == 0:
                    continue
                if kind == "topic":
                    words[mask] = offsets[idx] + rng.choice(len(topic_p[idx]), size=m, p=topic_p[idx])
                elif kind == "shared":
                    words[mask] = shared_offsets[idx] + rng.choice(len(shared_p[idx]), size=m, p=shared_p[idx])
                else:
                    words[mask] = bg_offset + rng.choice(len(bg_p), size=m, p=bg_p)
            # Dated events: in an event month, a share of the user's own-topic words name the event.
            starts = np.concatenate(([0], np.cumsum(lengths)[:-1]))
            word_month = np.repeat(tweet_months, lengths)
            ev_words = event_tables[c][word_month]
            hit = (which == 0) & (ev_words[:, 0] >= 0) & (rng.random(total) < args.event)
            if hit.any():
                pick = rng.integers(0, 6, size=int(hit.sum()))
                words[hit] = ev_words[hit, pick]
            counts = np.bincount(words, minlength=len(vocab))
            nz = np.nonzero(counts)[0]
            oracle_counts[u] = oracle_logp[:, nz] @ counts[nz]
            term_user_counts[nz] += 1
            user_counts[u] = (nz.astype(np.int32), counts[nz].astype(np.int32))
            # Timestamps: month by season; day and hour by a rhythm that is partly the community's
            # and mostly the user's own (each person keeps their own hours and days).
            personal_hours = np.full(24, 0.1)
            for peak_hour in rng.choice(24, size=2, replace=False):
                for d in range(-3, 4):
                    personal_hours[(peak_hour + d) % 24] += math.exp(-d * d / 3.0)
            personal_hours /= personal_hours.sum()
            user_hours = args.rhythm * hours + (1.0 - args.rhythm) * personal_hours
            personal_days = rng.uniform(0.5, 1.5, size=7)
            user_days = args.rhythm * days / days.sum() + (1.0 - args.rhythm) * personal_days / personal_days.sum()
            day = np.empty(count, dtype=np.int64)
            for mi in np.unique(tweet_months):
                sel = tweet_months == mi
                w = user_days[(month_day0[mi] + np.arange(month_len[mi]) + 3) % 7]
                day[sel] = month_day0[mi] + rng.choice(month_len[mi], size=int(sel.sum()), p=w / w.sum())
            minutes = day * 1440 + rng.choice(24, size=count, p=user_hours) * 60 + rng.integers(0, 60, size=count)
            index = np.argsort(-minutes, kind="stable")
            stamp_text = np.datetime_as_string(minutes.astype("datetime64[m]"), unit="m")
            lines = []
            for t_index in index:
                tweet_id = base_id + ((int(minutes[t_index]) - tweet_epoch_minutes) * 60 << 24) + rows_written
                text = " ".join(vocab[words[starts[t_index]:starts[t_index] + lengths[t_index]]])
                lines.append("{}\t{}\t{}\t{}\t{}\t{}\n".format(
                    names[c], tweet_id, stamp_text[t_index].replace("T", " "), user_ids[u], users[u], text))
                rows_written += 1
            out.write("".join(lines))
            assert rows_written < (1 << 24), "too many rows for unique tweet ids"
            tokens_written += total
            community_counts[c] += 1
            tweet_counts[c] += count
            truth.write("{},{},{},{:.4f},{:.4f},{},{:.4f},{}\n".format(
                users[u], user_ids[u], names[c], own[u], shared_w[u],
                names[cross_c[u]] if cross[u] else "", cross_w[u], count))
            done = int(community_counts.sum())
            if done % 500 == 0:
                print("  {} users, {} rows".format(done, rows_written), flush=True)

    # The oracle: knows every community's average word distribution (not each user's own mixture).
    predicted = oracle_counts.argmax(axis=1)
    oracle_accuracy = float((predicted == community_of).mean())
    majority = float(np.bincount(community_of, minlength=k).max() / n)

    similarity = similarity_check(user_counts, community_of, users, len(vocab), k)

    digest = hashlib.sha256()
    with open(out_path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    manifest = {
        "name": name, "seed": args.seed, "users": n, "rows": rows_written, "tokens": tokens_written,
        "vocabulary_used": int((term_user_counts > 0).sum()), "terms_used_by_two_or_more_users": int((term_user_counts >= 2).sum()), "communities": {names[c]: {"users": int(community_counts[c]),
                                                                       "tweets": int(tweet_counts[c])} for c in range(k)},
        "dates": [month_starts[0].strftime("%Y-%m-%d"), (month_starts[months] - dt.timedelta(days=1)).strftime("%Y-%m-%d")],
        "median_tweets_parameter": args.median_tweets, "mixture": {"own_scale": args.own_scale, "own_floor": args.own_floor, "season": args.season, "rhythm": args.rhythm, "event": args.event, "silent": args.silent,
        "cross": args.cross, "cross_max": args.cross_max}, "oracle_accuracy": round(oracle_accuracy, 4),
        "majority_baseline": round(majority, 4), "similarity_recomputed": similarity, "bytes": os.path.getsize(out_path), "sha256": digest.hexdigest(),
        "lexicon_sizes": {t[0]: len(t[1]) for t in topics},
        "shared_topics": {s[0]: {"members": s[1], "words": len(s[2])} for s in shared},
        "background_words": len(background),
    }
    with open(os.path.join(HERE, name + "-manifest.json"), "w") as handle:
        json.dump(manifest, handle, indent=2)
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    sys.exit(main())
