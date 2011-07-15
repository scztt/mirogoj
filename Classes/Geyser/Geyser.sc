Geyser {

	classvar geysernotes, <gdata;
	var <server, <group, <gbarksynth, <ggrainsynth, <gconvsynth, <routesynths;
	var <tobark, <toconv, <topanner, gdur, <gsnd;
	
	*initClass {
		geysernotes = CtkProtoNotes(
	
			SynthDef(\ggrain, {arg out, barkout, convout, buf, dur, ratemax=100, ratemin=2, durmin=0.01, durmax=1, pan=0, amp=1;
				var cleanenv, grnrateenv, grndurenv, trig, pointer, grn, panenv;
					SeedMaster.ugen;
					cleanenv = EnvGen.kr( Env( [0,0.02,1,0.8, 0, 0], [0.34,0.02, 0.27, 0.23, 0.14], [3,2,1,-3,0] ), levelScale: amp, timeScale: dur, doneAction: 2 );
					grnrateenv = EnvGen.kr( Env([ratemax, ratemin],[1],[-3]), timeScale:dur);
					grndurenv = EnvGen.kr( Env( [durmin, durmax, durmax*0.2],[0.4,0.6],[-1,3] ), timeScale:dur);
					
					trig = Dust.kr(grnrateenv);
					pointer = LFNoise0.kr(ratemax).range(0, 0.9); 
					grn = BufGrain.ar(trig, grndurenv, buf, 1, pointer);
					
						// for single-channel geysir bursts
					//Out.ar(out, PanAz.ar(12, grn * cleanenv, pan, width: 1)); 
						// for the quad spreading routing
					panenv = EnvGen.kr( Env( [-1,-1,1],[0.36, 0.64],[0, -3] ), timeScale: dur);
					Out.ar( out, Pan2.ar( grn * cleanenv, panenv ) );
					
					Out.ar(convout, LPF.ar(grn * amp, 900) );
					Out.ar(barkout, grn * amp);
				}),
		
			SynthDef(\gconv, {arg inbus, out, barkout, kernelbuf, dur, convscale=0.5, pan=0;
				var convenv, kernel, conv, sig;
					SeedMaster.ugen;
					convenv = EnvGen.kr( Env( [0,1,1,0], [0.05, 0.35, 0.02], 2 ), timeScale: dur, doneAction: 2 );
					
					kernel = PlayBuf.ar( 1, kernelbuf, 1, loop: 1);
					conv = Convolution.ar( In.ar(inbus, 1), kernel, 4096, convscale);
					sig = conv * convenv * 0.05;
						// for single-channel geysers in a 12-channel system
					//Out.ar( out, PanAz.ar(12, sig, pan, width: 1) ); 
					Out.ar( out, sig );
					Out.ar( barkout, sig );
				}),
		
			SynthDef(\gbark, {arg out, inbus, dur, fb=0.7, fshift = 1, grndur=0.3, grnratemin=3, grnratemax=13, delaymin=0.1, delaymax=0.7, delayspread=0.2, pan=0, amp = 1;
				var barkenv, trigrate, src, deltimes, ampvals, delamt, bdelays, grntrig, grnrate, graindur, panenv;
					SeedMaster.ugen;
					barkenv = EnvGen.kr( 
						Env( [0,0.005,0.05,1,0.1, 0.15, 0.9, 0], 
							[0.25, 0.1, 0.02, 0.02,0.26, 0.35, ((delaymax)/dur)].normalizeSum, 
							[2,2,5,2,-2,-3] ),
							timeScale: (dur+ (delaymax)), levelScale:amp, doneAction:2 );
					grnrate = EnvGen.kr( Env( [grnratemax,grnratemin], [dur], [-3] ) );
					grntrig = Dust.kr( EnvGen.kr( Env([grnratemax,grnratemin], [dur], [-3]) ) );
					graindur = EnvGen.kr( Env([1, 0.35, 0.05]*grndur,[0.65,0.35], [-4]), timeScale: dur );
			
					src = InGrain.ar( grntrig, graindur, In.ar(inbus, 1) );
					deltimes = Array.fill( 25, 0.0 );
					ampvals = Array.fill( 25, 1.0 );
						// populate the arrays...	
					25.do{arg i;
							// fill with static values...
						ampvals[i] = Rand(0.4, 0.95);
							// or with dynamic values
						deltimes[i] = Rand(delaymin, delaymax) * delayspread;
						};
					bdelays = BarkDelay.ar(src, deltimes, fb, ampvals, 
							2, 		//maximum delay
							0.25, 	//bandwidth multiplier
							1);  	//freqmul
						
						//for single-channel geysers
					// Out.ar( out, PanAz.ar(12, bdelays.sum * barkenv, pan, width: 1) );
						// for quad-routed geysers
					panenv = EnvGen.kr( Env( [-1,-1,1],[0.36, 0.64],[0, -3] ), timeScale: dur);
					Out.ar( out, Pan2.ar( bdelays.sum * barkenv, panenv ) );
					
				}),
		
				SynthDef(\routeToOutput, { arg in, out, dur;
						//just to kill synth, dur*1.5 to allow delays to settle
					EnvGen.kr( Env( [1,1], [dur*1.5], \sin ), doneAction:2 );
					Out.ar( out, In.ar(in, 1) )
					})
		);
		
		gdata = GeyserData.new;
		
	}
	
	*new { | server, clusternum, sndfile, kernel, dur, amp=0.5 |
		^super.newCopyArgs(server).init( server, clusternum, sndfile, kernel, dur, amp )
	}
	
	init { | argserver, clusternum, sndfile, kernel, dur, amp |
		var virtMeters;
		server = argserver ?? {Server.default};
		group = CtkGroup.play( server: server, addAction: \tail );
		tobark = CtkAudio.new;
		toconv = CtkAudio.new;
		topanner = CtkAudio.new(2);
		gdur = dur;
		routesynths = [];
		gsnd = sndfile;

		
		ggrainsynth = geysernotes[\ggrain].new(addAction: \head, target: group)
			.out_(topanner).barkout_(tobark).convout_(toconv).dur_(dur).buf_(sndfile).ratemax_(50).ratemin_(7)
			.durmin_(0.2).durmax_(0.55).amp_(amp).randSeed_(SeedMaster.seed);
		gconvsynth = geysernotes[\gconv].new(addAction: \after, target: ggrainsynth)
			.inbus_(toconv).out_(topanner).barkout_(tobark).kernelbuf_(kernel).dur_(dur).convscale_(0.25);
		gbarksynth = geysernotes[\gbark].new(addAction: \after, target: gconvsynth)
			.out_(topanner).inbus_(tobark).dur_(dur).fb_(0.2).grndur_(1.2).grnratemin_(5).grnratemax_(35)
			.delaymin_(0.4).delaymax_(2.7).delayspread_(0.3).amp_(4.dbamp).randSeed_(SeedMaster.seed);

		virtMeters = VirtualSpeakerArray.get.getBusNums;
		
			// create output routing synths
			// origin routing
		gdata.clusterSpeakers(clusternum).do({ |speaker|
			speaker.notNil.if({
				routesynths = routesynths.add( 
					//geysernotes[\routeToOutput].new(addAction: \tail, target: group).in_(topanner).out_(speaker).dur_(dur));
					geysernotes[\routeToOutput].new(addAction: \tail, target: group).in_(topanner).out_(virtMeters[speaker]).dur_(dur));
			})
		});
			// the periphery routing
		gdata.getPeriphery(clusternum).do({ | speaker |
			speaker.notNil.if({
				routesynths = routesynths.add( 
					//geysernotes[\routeToOutput].new(addAction: \tail, target: group).in_(topanner.bus+1).out_(speaker).dur_(dur) );
					geysernotes[\routeToOutput].new(addAction: \tail, target: group).in_(topanner.bus+1).out_(virtMeters[speaker]).dur_(dur) );
			})
		});
	}
	
	play {
		var wait;
		SystemClock.sched( 0.1, {
			ggrainsynth.play(group);
			});
		SystemClock.sched( 0.5, {	
			gconvsynth.play(group);
			});
		SystemClock.sched( 0.9, {	
			gbarksynth.play(group); 	
			routesynths.do({ | synth | synth.play( group ) });
			});
		wait = gdur + gbarksynth.delaymax;
//		tobark.bus.postln;
//		toconv.bus.postln;
//		topanner.bus.postln;
		
		SystemClock.sched( wait,
			{ "freeing busses and groups".postln; group.deepFree; tobark.free; toconv.free; topanner.free; }
			);
	}
}
			