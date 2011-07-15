Breath {
	classvar <protos, <breathData;
	var <server, <group, <synth, <virtbusses, <outsynths, <wait;
	var name, numouts, <atsbuffer, <atsmidpoint, <atsduration, targetdur, <xpand, <howwide;
	var skpBnd, synthamp, synthrolloff, panenv;

	*initClass {
		Class.initClassTree(BreathData);
		Class.initClassTree(InterplEnv);
		Class.initClassTree(AtsFile);
//		Class.initClassTree(EnvGen);
//		Class.initClassTree(Env);
//		Class.initClassTree(SinOsc);
		
		protos = CtkProtoNotes(		
				// for use with a soundfile			
//				SynthDef(\breath_6o, { arg out=0, numlayers=6, expandRange=1, width=0.7, rolloff=0.5,
//					inbuf, exbuf, indur, exdur, targdur, amp=1, gate=1;
//				var rate, idur, xdur, env, exenv, inenv, xpntrenv, inhale, xhale, 
//					maxPan, panenv, scalepan, widthenv;
//					rate = targdur/(indur+exdur);
//					maxPan = 1.7 * expandRange;
//					idur = indur*rate;
//					xdur = exdur*rate;
//					env = EnvGen.kr( Env([0,1,1,1,0],[0.1, idur-0.1, xdur-0.1, 0.1],\sin, 2), gate, levelScale: amp, doneAction:2 );
//					exenv = EnvGen.kr( Env([0,0,1,1,0],[0.1, 0.1, (xdur)-0.7, 0.5],\sin, 1), gate, levelScale: amp );
//					inenv = EnvGen.kr( Env([0,1,1,0],[0.05,(idur)-0.55, 0.5],\sin) );
//					
//					xpntrenv = EnvGen.kr( Env([0,0,0.85],[0.1,xdur],\lin, 1), gate);
//					inhale = Warp1.ar(1, inbuf, Line.kr(0,0.85,idur), 1, 0.45, -1, 10, 0.05, 2) * inenv;
//					xhale = Warp1.ar(1, exbuf, xpntrenv, 1, 0.45, -1, 10, 0.05, 2) * exenv;
//					
////					inhale = PlayBuf.ar( 1, inbuf, rate.reciprocal, loop: 0 );
////					xhale = PlayBuf.ar( 1, exbuf, rate.reciprocal, (gate<1) ) * exenv;
//						// controls the pan breadth from the origin
//					panenv = EnvGen.kr( Env( [0,0.05,1,0.05,0],[idur,idur,xdur,xdur]*[0.3,0.7,0.7,0.3],[1,1,-1,-1], 2 ), gate );
//						// scales the panning to compensate for layer distances
//					scalepan = IEnvGen.kr(
//						 Control.names(\panscale).kr(Env.newClear(9).asArray), panenv ) * maxPan;
//						// controls the breadth of the breath through space, tracking the panenv
//					widthenv = IEnvGen.kr(
//						InterplEnv([1.2, width*numlayers*expandRange*0.85, 1],maxPan*[0.5,0.5],[1.3, -1.3]),
//						scalepan );
//					rolloff = IEnvGen.kr(
//						InterplEnv([1, rolloff],[maxPan],\sin), scalepan );
//					Out.ar( out, PanAz.ar(6, inhale+xhale, scalepan, 0.5, widthenv, 0) * env * rolloff)
//			}),

			SynthDef(\breath_9o, { arg out=0, numlayers=9, expandRange=1, width=0.7, rolloff=0.5,
					atsbuf, atsdur, atsmidpnt, targdur, skipBnd, amp=1, gate=1;
				
					var pntr, cf, bw, env, bands, maxPan, panenv, scalepan, widthenv, sigout, verb;

					cf = [ 50, 150, 250, 350, 455, 570, 700, 845, 1000, 
						1175, 1375, 1600, 1860, 2160, 2510, 2925, 3425, 4050, 
						4850, 5850, 7050, 8600, 10750, 13750, 17750 ];
					bw = [100, 100, 100, 100, 110, 120, 140, 150, 160, 
						190, 210, 240, 280, 320, 380, 450, 550, 700, 900, 1100, 
						1300, 1800, 2500, 3500, 4500 ];
					maxPan = 1.7 * expandRange;

					env = EnvGen.kr( Env([0,1,1,0.2,1,1,0],[0.1, (atsmidpnt*targdur)-0.8, 0.7, 0.7, ((1-atsmidpnt)*targdur)-0.8, 0.1+4],\sin, 3), 
						gate, levelScale: amp, doneAction:2 );
					
					pntr = EnvGen.kr( Env( [0, atsmidpnt, 1],[atsmidpnt, 1-atsmidpnt], \lin, 1 ), gate, timeScale: targdur);
					bands = Array.fill(15, nil);
					15.do{ | i |
						bands[i] = AtsBand.ar(atsbuf, ((i*skipBnd)%25), pntr)
//						bands[i] = AtsNoise.kr(atsbuf, i, 
//							EnvGen.kr( Env( [0, atsmidpnt, 1],[atsmidpnt, 1-atsmidpnt], \lin, 1 ), gate, timeScale: targdur),
//							1 ) * LFNoise1.ar(bw[i]) * SinOsc.ar(cf[i]) //* noiseScaler
						};
						// controls the pan breadth from the origin
					panenv = EnvGen.kr( Env( [0,0.05,1,0.05,0],[atsmidpnt,atsmidpnt,1-atsmidpnt,1-atsmidpnt]*[0.3,0.7,0.7,0.3],[1,1,-1,-1], 2 ), 
							gate, timeScale: targdur );
						// scales the panning to compensate for layer distances
					scalepan = IEnvGen.kr(
						 Control.names(\panscale).kr(Env.newClear(9).asArray), panenv ) * maxPan;
						// controls the breadth of the breath through space, tracking the panenv
					widthenv = IEnvGen.kr(
						InterplEnv([1.2, width*numlayers*expandRange*0.85, 1.6],maxPan*[0.5,0.5],[1.3, -1.3]),
						scalepan );
					rolloff = IEnvGen.kr( InterplEnv([1, rolloff],[maxPan],\sin), scalepan );
					
					sigout = bands.sum * env * rolloff;
					
					verb = GVerb.ar( sigout,
						202.34, 6.13, 0.92, 0.789, 15, -8.dbamp, -13.dbamp, -9.dbamp, 202.34, 1).sum;
					
					Out.ar( out, PanAz.ar(9, verb,
							//PitchShift.ar(bands.sum, 0.02, Rand(0.9,3), 0, 0.0001)
							scalepan, 0.5, widthenv, 0) )
			}),
			
			SynthDef(\breath_8o, { arg out=0, numlayers=8, expandRange=1, width=0.7, rolloff=0.5,
					atsbuf, atsdur, atsmidpnt, targdur, skipBnd, amp=1, gate=1;
				
					var pntr, cf, bw, env, bands, maxPan, panenv, scalepan, widthenv, sigout, verb;

					cf = [ 50, 150, 250, 350, 455, 570, 700, 845, 1000, 
						1175, 1375, 1600, 1860, 2160, 2510, 2925, 3425, 4050, 
						4850, 5850, 7050, 8600, 10750, 13750, 17750 ];
					bw = [100, 100, 100, 100, 110, 120, 140, 150, 160, 
						190, 210, 240, 280, 320, 380, 450, 550, 700, 900, 1100, 
						1300, 1800, 2500, 3500, 4500 ];
					maxPan = 1.7 * expandRange;

					env = EnvGen.kr( Env([0,1,1,0.2,1,1,0],[0.1, (atsmidpnt*targdur)-0.8, 0.7, 0.7, ((1-atsmidpnt)*targdur)-0.8, 0.1+4],\sin, 3), 
						gate, levelScale: amp, doneAction:2 );
					
					pntr = EnvGen.kr( Env( [0, atsmidpnt, 1],[atsmidpnt, 1-atsmidpnt], \lin, 1 ), gate, timeScale: targdur);
					bands = Array.fill(15, nil);
					15.do{ | i |
						bands[i] = AtsBand.ar(atsbuf, ((i*skipBnd)%25), pntr)
//						bands[i] = AtsNoise.kr(atsbuf, i, 
//							EnvGen.kr( Env( [0, atsmidpnt, 1],[atsmidpnt, 1-atsmidpnt], \lin, 1 ), gate, timeScale: targdur),
//							1 ) * LFNoise1.ar(bw[i]) * SinOsc.ar(cf[i]) //* noiseScaler
						};
						// controls the pan breadth from the origin
					panenv = EnvGen.kr( Env( [0,0.05,1,0.05,0],[atsmidpnt,atsmidpnt,1-atsmidpnt,1-atsmidpnt]*[0.3,0.7,0.7,0.3],[1,1,-1,-1], 2 ), 
							gate, timeScale: targdur );
						// scales the panning to compensate for layer distances
					scalepan = IEnvGen.kr(
						 Control.names(\panscale).kr(Env.newClear(9).asArray), panenv ) * maxPan;
						// controls the breadth of the breath through space, tracking the panenv
					widthenv = IEnvGen.kr(
						InterplEnv([1.2, width*numlayers*expandRange*0.85, 1.6],maxPan*[0.5,0.5],[1.3, -1.3]),
						scalepan );
					rolloff = IEnvGen.kr( InterplEnv([1, rolloff],[maxPan],\sin), scalepan );
					
					sigout = bands.sum * env * rolloff;
					
					verb = GVerb.ar( sigout,
						202.34, 6.13, 0.92, 0.789, 15, -8.dbamp, -13.dbamp, -9.dbamp, 202.34, 1).sum;
						
					Out.ar( out, PanAz.ar(8, verb,
							//PitchShift.ar(bands.sum, 0.02, Rand(0.9,3), 0, 0.0001)
							scalepan, 0.5, widthenv, 0) )
			}),

			SynthDef(\breath_6o, { arg out=0, numlayers=6, expandRange=1, width=0.7, rolloff=0.5,
					atsbuf, atsdur, atsmidpnt, targdur, skipBnd, amp=1, gate=1;
				
					var pntr, cf, bw, env, bands, maxPan, panenv, scalepan, widthenv, sigout, verb;

					cf = [ 50, 150, 250, 350, 455, 570, 700, 845, 1000, 
						1175, 1375, 1600, 1860, 2160, 2510, 2925, 3425, 4050, 
						4850, 5850, 7050, 8600, 10750, 13750, 17750 ];
					bw = [100, 100, 100, 100, 110, 120, 140, 150, 160, 
						190, 210, 240, 280, 320, 380, 450, 550, 700, 900, 1100, 
						1300, 1800, 2500, 3500, 4500 ];
					maxPan = 1.7 * expandRange;

					env = EnvGen.kr( Env([0,1,1,0.2,1,1,0],[0.1, (atsmidpnt*targdur)-0.8, 0.7, 0.7, ((1-atsmidpnt)*targdur)-0.8, 0.1+4],\sin, 3), 
						gate, levelScale: amp, doneAction:2 );
					
					pntr = EnvGen.kr( Env( [0, atsmidpnt, 1],[atsmidpnt, 1-atsmidpnt], \lin, 1 ), gate, timeScale: targdur);
					bands = Array.fill(15, nil);
					15.do{ | i |
						bands[i] = AtsBand.ar(atsbuf, ((i*skipBnd)%25), pntr)
//						bands[i] = AtsNoise.kr(atsbuf, i, 
//							EnvGen.kr( Env( [0, atsmidpnt, 1],[atsmidpnt, 1-atsmidpnt], \lin, 1 ), gate, timeScale: targdur),
//							1 ) * LFNoise1.ar(bw[i]) * SinOsc.ar(cf[i]) //* noiseScaler
						};
						// controls the pan breadth from the origin
					panenv = EnvGen.kr( Env( [0,0.05,1,0.05,0],[atsmidpnt,atsmidpnt,1-atsmidpnt,1-atsmidpnt]*[0.3,0.7,0.7,0.3],[1,1,-1,-1], 2 ), 
							gate, timeScale: targdur );
						// scales the panning to compensate for layer distances
					scalepan = IEnvGen.kr(
						 Control.names(\panscale).kr(Env.newClear(9).asArray), panenv ) * maxPan;
						// controls the breadth of the breath through space, tracking the panenv
					widthenv = IEnvGen.kr(
						InterplEnv([1.2, width*numlayers*expandRange*0.85, 1.6],maxPan*[0.5,0.5],[1.3, -1.3]),
						scalepan );
					rolloff = IEnvGen.kr( InterplEnv([1, rolloff],[maxPan],\sin), scalepan );
					
					sigout = bands.sum * env * rolloff;
					
					verb = GVerb.ar( sigout,
						202.34, 6.13, 0.92, 0.789, 15, -8.dbamp, -13.dbamp, -9.dbamp, 202.34, 1).sum;
					
					Out.ar( out, PanAz.ar(6, verb,
							//PitchShift.ar(bands.sum, 0.02, Rand(0.9,3), 0, 0.0001)
							scalepan, 0.5, widthenv, 0) )
			}),

			SynthDef(\breath_5o, { arg out=0, numlayers=5, expandRange=1, width=0.7, rolloff=0.5,
					atsbuf, atsdur, atsmidpnt, targdur, skipBnd, amp=1, gate=1;
				
					var pntr, cf, bw, env, bands, maxPan, panenv, scalepan, widthenv, sigout, verb;

					cf = [ 50, 150, 250, 350, 455, 570, 700, 845, 1000, 
						1175, 1375, 1600, 1860, 2160, 2510, 2925, 3425, 4050, 
						4850, 5850, 7050, 8600, 10750, 13750, 17750 ];
					bw = [100, 100, 100, 100, 110, 120, 140, 150, 160, 
						190, 210, 240, 280, 320, 380, 450, 550, 700, 900, 1100, 
						1300, 1800, 2500, 3500, 4500 ];
					maxPan = 1.7 * expandRange;

					env = EnvGen.kr( Env([0,1,1,0.2,1,1,0],[0.1, (atsmidpnt*targdur)-0.8, 0.7, 0.7, ((1-atsmidpnt)*targdur)-0.8, 0.1+4],\sin, 3), 
						gate, levelScale: amp, doneAction:2 );
					
					pntr = EnvGen.kr( Env( [0, atsmidpnt, 1],[atsmidpnt, 1-atsmidpnt], \lin, 1 ), gate, timeScale: targdur);
					bands = Array.fill(15, nil);
					15.do{ | i |
						bands[i] = AtsBand.ar(atsbuf, ((i*skipBnd)%25), pntr)
//						bands[i] = AtsNoise.kr(atsbuf, i, 
//							EnvGen.kr( Env( [0, atsmidpnt, 1],[atsmidpnt, 1-atsmidpnt], \lin, 1 ), gate, timeScale: targdur),
//							1 ) * LFNoise1.ar(bw[i]) * SinOsc.ar(cf[i]) //* noiseScaler
						};
						// controls the pan breadth from the origin
					panenv = EnvGen.kr( Env( [0,0.05,1,0.05,0],[atsmidpnt,atsmidpnt,1-atsmidpnt,1-atsmidpnt]*[0.3,0.7,0.7,0.3],[1,1,-1,-1], 2 ), 
							gate, timeScale: targdur );
						// scales the panning to compensate for layer distances
					scalepan = IEnvGen.kr(
						 Control.names(\panscale).kr(Env.newClear(9).asArray), panenv ) * maxPan;
						// controls the breadth of the breath through space, tracking the panenv
					widthenv = IEnvGen.kr(
						InterplEnv([1.2, width*numlayers*expandRange*0.85, 1.6],maxPan*[0.5,0.5],[1.3, -1.3]),
						scalepan );
					rolloff = IEnvGen.kr( InterplEnv([1, rolloff],[maxPan],\sin), scalepan );
					
					sigout = bands.sum * env * rolloff;
					
					verb = GVerb.ar( sigout,
						202.34, 6.13, 0.92, 0.789, 15, -8.dbamp, -13.dbamp, -9.dbamp, 202.34, 1).sum;
						
					Out.ar( out, PanAz.ar(5,verb,
							//PitchShift.ar(bands.sum, 0.02, Rand(0.9,3), 0, 0.0001)
							scalepan, 0.5, widthenv, 0) )
			}),
			
			SynthDef(\breathRouteToOutput, { arg in, out;
				Out.ar( out, In.ar(in, 1) )
				})
		);
		breathData = BreathData.new;

	}

	*new { | server, clusternum, atsbuf, atsdur, atsmidpnt, skpBand, amp=1, rolloff=1 |		^super.newCopyArgs(server).init( server, clusternum, atsbuf, atsdur, atsmidpnt, skpBand, amp, rolloff )
	}

	init { | argserver, clusternum, atsbuf, atsdur, atsmidpnt, argskpBand, amp, rolloff |
		var virtMeters;
		server = argserver ?? {Server.default};
		group = CtkGroup.play( server:server, addAction: \tail );

		synthamp = amp;
		synthrolloff = rolloff;
		atsbuffer = atsbuf;
		atsduration = atsdur;
		atsmidpoint = atsmidpnt;
		skpBnd = argskpBand;
		virtMeters = VirtualSpeakerArray.get.getBusNums;
		
		panenv = breathData.panEnv( clusternum ); //InterplEnv([0,1],[1],[0]);
		outsynths = Array.new;
		numouts = breathData.numVirtBusses( clusternum );
		name = 'breath_'++numouts.asSymbol++'o';
		virtbusses = CtkAudio.new(numouts);
			// create the output routing synths
		breathData.clustersByLayer( clusternum ).do({ | layer, i |
			layer.do({	| cluster |
				breathData.clusterSpeakers( cluster ).do({ | speaker |
					speaker.notNil.if({
						outsynths = outsynths.add(
							protos[\breathRouteToOutput].new(addAction: \tail, target: group, server: server)
								.in_(virtbusses.bus + i) // assumes the busses are continuous!!!!!!!!!
								.out_(virtMeters[speaker]).play( group ) ); // play through the simulation
								//.out_(speaker).play( group ) );
					})
				})
			})
				// for playback in 117, each speaker is a cluster
//			layer.do({	| cluster |
//					outsynths = outsynths.add(
//						protos[\breathRouteToOutput].new(addAction: \tail, target: group, server: server)
//							.in_(virtbusses.bus + i) // assumes the busses are continuous!!!!!!!!!
//							//.out_(cluster).play( group ) ); //// uncomment for 12 channel cluster verstion
//							.out_(rand(2)).play( group ) ); //// just testing in 2 channels
//				})

		});
					
		//create its own thread?
		CmdPeriod.add({ group.deepFree; virtbusses.free });
	}

	inhale { | targetdur, expandRange=0.9, width=0.7 |
		xpand = expandRange;
		howwide = width;
		synth = protos[name].new(addAction: \head, target: group, server: server)
			.out_(virtbusses)
//			.out_(0)
 			.expandRange_( xpand )	// how far of the total range you want to expand from origin, 0 to 1
			.width_( howwide )				// breadth fo the breath spread, 0 to 1
			.rolloff_( synthrolloff )
			.atsbuf_( atsbuffer )
			.atsmidpnt_( atsmidpoint )
			.atsdur_( atsduration )
			.targdur_( targetdur )
			.skipBnd_(skpBnd)
			.amp_( synthamp )
			.panscale_( panenv );			// the envelope for the scaling panning pointer for layer distances
		synth.play( group );
	}

	exhale {
		synth.release
	}

	free {
		group.deepFree;
		virtbusses.free;
	}

}